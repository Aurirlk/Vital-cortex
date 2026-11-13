package cn.kmbeast.crm.vectordb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 向量库完整性测试（阶段 0-A / A2）
 *
 * <p><b>整改背景</b>：本机 {@code health_knowledge} 集合实测存在三处不一致——
 * <pre>
 *   metadata.json 声明 docCount = 312
 *   vectors.bin                = 1277952 bytes → 1277952/4/1536 = 208 条
 *   documents.json             = 312 条
 * </pre>
 *
 * <p>而 {@code cosineSearchTopK} 的循环上限是<b>向量条数</b>(208)，
 * 于是下标 208~311 这 <b>104 篇文档永久不可召回，且整个过程不报任何错</b> ——
 * 典型的"静默数据丢失"，用户只会觉得"AI 知识库有时答不上来"，却查不到原因。
 *
 * <p>本测试锁死三件事：
 * <ol>
 *   <li>{@code effectiveCount} 三者取最小，且负数/零不炸。</li>
 *   <li>{@code availableCount} 能如实算出文件实际条数（不依赖元数据声明）。</li>
 *   <li>端到端：构造一个"312/208/312"的病态集合，
 *       {@code loadCollection} 之后<b>向量、文档、范数三者必须严格等长</b> ——
 *       这是下标对齐的唯一保证，错一位就意味着张冠李戴的检索结果。</li>
 * </ol>
 */
@DisplayName("向量库完整性（下标对齐）")
class LocalVectorStoreIntegrityTest {

    private static final int DIMENSION = 1536;

    // ==================== 一、effectiveCount 取最小 ====================

    @Test
    @DisplayName("三者不一致时取最小值（真实故障场景 312/208/312）")
    void effectiveCountTakesMin() {
        assertEquals(208, LocalVectorStoreImpl.effectiveCount(312, 208, 312),
                "必须以向量实际条数为准，否则检索会访问到不存在的下标");
    }

    @Test
    @DisplayName("文档数最少时以文档数为准")
    void effectiveCountLimitedByDocs() {
        assertEquals(5, LocalVectorStoreImpl.effectiveCount(10, 10, 5));
    }

    @Test
    @DisplayName("声明数最少时以声明数为准")
    void effectiveCountLimitedByDeclared() {
        assertEquals(3, LocalVectorStoreImpl.effectiveCount(3, 100, 100));
    }

    @Test
    @DisplayName("三者一致时原样返回")
    void effectiveCountAllEqual() {
        assertEquals(10, LocalVectorStoreImpl.effectiveCount(10, 10, 10));
    }

    @Test
    @DisplayName("负数与零不炸，归零处理")
    void effectiveCountNonNegative() {
        assertEquals(0, LocalVectorStoreImpl.effectiveCount(0, 0, 0));
        assertEquals(0, LocalVectorStoreImpl.effectiveCount(-5, 10, 10),
                "声明数为负不应产生负长度列表");
        assertEquals(0, LocalVectorStoreImpl.effectiveCount(10, -1, 10),
                "向量数为负不应产生负长度列表");
    }

    // ==================== 二、availableCount 如实计算 ====================

    @Test
    @DisplayName("availableCount 按文件字节数如实计算，不信元数据")
    void availableCountReadsFile(@TempDir Path dir) throws IOException {
        Path bin = dir.resolve("vectors.bin");
        writeFakeVectors(bin, 7);

        LocalVectorStoreImpl.VectorBinaryStore store =
                new LocalVectorStoreImpl.VectorBinaryStore(bin, DIMENSION);
        assertEquals(7, store.availableCount(),
                "写了 7 条就应该是 7 条，不能因为 metadata 写了 312 就报 312");
    }

    @Test
    @DisplayName("availableCount：文件不存在返回0")
    void availableCountMissingFile(@TempDir Path dir) throws IOException {
        LocalVectorStoreImpl.VectorBinaryStore store =
                new LocalVectorStoreImpl.VectorBinaryStore(dir.resolve("nope.bin"), DIMENSION);
        assertEquals(0, store.availableCount());
    }

    @Test
    @DisplayName("availableCount：维度为0不触发除零")
    void availableCountZeroDimension(@TempDir Path dir) throws IOException {
        Path bin = dir.resolve("vectors.bin");
        Files.write(bin, new byte[]{1, 2, 3, 4});
        LocalVectorStoreImpl.VectorBinaryStore store =
                new LocalVectorStoreImpl.VectorBinaryStore(bin, 0);
        assertEquals(0, store.availableCount(), "维度0必须返回0而不是抛 ArithmeticException");
    }

    @Test
    @DisplayName("readAll 截断到实际可读条数")
    void readAllTruncates(@TempDir Path dir) throws IOException {
        Path bin = dir.resolve("vectors.bin");
        writeFakeVectors(bin, 3);

        LocalVectorStoreImpl.VectorBinaryStore store =
                new LocalVectorStoreImpl.VectorBinaryStore(bin, DIMENSION);
        // 谎报 100 条，实际只应返回 3 条
        assertEquals(3, store.readAll(100).length);
    }

    // ==================== 三、端到端：三者必须等长 ====================

    @Test
    @DisplayName("🔴 端到端：病态集合(312/208/312)载入后向量与文档严格等长")
    void loadCollectionAlignsAll(@TempDir Path dir) throws Exception {
        buildBrokenCollection(dir, 312, 208);

        LocalVectorStoreImpl impl = newStore(dir);
        invokeLoadCollection(impl, "health_knowledge");

        int vecs = sizeOf(impl, "vectorsMap");
        int norms = sizeOf(impl, "normsMap");
        int docs = sizeOf(impl, "documentsMap");

        assertEquals(208, vecs, "向量数应为实际可读的 208");
        assertEquals(208, docs, "文档数必须截断到与向量一致，否则下标错位");
        assertEquals(208, norms, "范数数必须同步，否则第 i 条会用到别人的范数");

        assertEquals(vecs, docs, "🔴 向量与文档必须等长，这是 index 对齐的唯一保证");
        assertEquals(vecs, norms, "🔴 向量与范数必须等长");
    }

    @Test
    @DisplayName("端到端：完好集合(10/10/10)不受影响")
    void loadCollectionHealthyUnchanged(@TempDir Path dir) throws Exception {
        buildBrokenCollection(dir, 10, 10);

        LocalVectorStoreImpl impl = newStore(dir);
        invokeLoadCollection(impl, "health_knowledge");

        assertEquals(10, sizeOf(impl, "vectorsMap"));
        assertEquals(10, sizeOf(impl, "documentsMap"));
        assertEquals(10, sizeOf(impl, "normsMap"));
    }

    // ==================== 辅助 ====================

    /** 写入 n 条伪造向量（每条 DIMENSION 个 float） */
    private static void writeFakeVectors(Path bin, int n) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(DIMENSION * 4 * n).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < DIMENSION; d++) {
                buf.putFloat(0.01f * d);
            }
        }
        buf.flip();
        Files.write(bin, buf.array());
    }

    /**
     * 构造一个"元数据谎报 declared 条、实际只有 actual 条向量"的目录结构，
     * 复刻本机真实故障。
     */
    private static void buildBrokenCollection(Path root, int declared, int actual) throws IOException {
        Path col = root.resolve("health_knowledge");
        Files.createDirectories(col);
        writeFakeVectors(col.resolve("vectors.bin"), actual);

        String meta = "{\"collectionName\":\"health_knowledge\",\"dimension\":" + DIMENSION
                + ",\"docCount\":" + declared + ",\"nextId\":" + (declared + 1)
                + ",\"idIndex\":[],\"deletedIds\":[]}";
        Files.writeString(col.resolve("metadata.json"), meta, java.nio.charset.StandardCharsets.UTF_8);

        StringBuilder docs = new StringBuilder("{\"version\":1,\"documents\":{");
        for (int i = 1; i <= declared; i++) {
            if (i > 1) docs.append(",");
            docs.append("\"").append(i).append("\":{\"id\":").append(i)
                    .append(",\"content\":\"文档").append(i).append("\"}");
        }
        docs.append("}}");
        Files.writeString(col.resolve("documents.json"), docs.toString(),
                java.nio.charset.StandardCharsets.UTF_8);

        String collections = "{\"version\":1,\"collections\":[{\"name\":\"health_knowledge\",\"dimension\":"
                + DIMENSION + ",\"doc_count\":" + declared + "}]}";
        Files.writeString(root.resolve("collections.json"), collections,
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static LocalVectorStoreImpl newStore(Path root) throws Exception {
        LocalVectorStoreImpl impl = new LocalVectorStoreImpl();
        Field f = LocalVectorStoreImpl.class.getDeclaredField("storeRoot");
        f.setAccessible(true);
        f.set(impl, root.toString());
        return impl;
    }

    private static void invokeLoadCollection(LocalVectorStoreImpl impl, String name) throws Exception {
        Method m = LocalVectorStoreImpl.class.getDeclaredMethod("loadCollection", String.class);
        m.setAccessible(true);
        m.invoke(impl, name);
    }

    @SuppressWarnings("unchecked")
    private static int sizeOf(LocalVectorStoreImpl impl, String mapField) throws Exception {
        Field f = LocalVectorStoreImpl.class.getDeclaredField(mapField);
        f.setAccessible(true);
        Map<String, List<?>> map = (Map<String, List<?>>) f.get(impl);
        List<?> list = map.get("health_knowledge");
        return list == null ? 0 : list.size();
    }
}
