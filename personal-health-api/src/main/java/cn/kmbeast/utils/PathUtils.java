package cn.kmbeast.utils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件存储根路径解析工具。
 *
 * <p>MM-06 整改：原实现依赖 {@code getClassLoader().getResource("")} 并硬编码剥离
 * {@code /target/classes}，这是 IDE 专有写法——用 {@code java -jar} 或在 Docker 中启动时，
 * 应用类加载器对空路径返回 {@code null}，直接 NPE，导致上传/下载/资讯配图全部 500。
 *
 * <p>新的解析优先级：
 * <ol>
 *   <li>系统属性 {@code file.storage.root} 或环境变量 {@code FILE_STORAGE_ROOT}（生产推荐，
 *       指向挂载出来的持久化卷）；</li>
 *   <li>类路径根目录（IDE / exploded 部署时可用，保持老行为兼容）；</li>
 *   <li>进程工作目录下的 {@code ./data}（兜底，永不返回 null）。</li>
 * </ol>
 * 解析结果会被缓存，并确保目录已创建。
 */
public class PathUtils {

    private static final String PROP_KEY = "file.storage.root";
    private static final String ENV_KEY = "FILE_STORAGE_ROOT";
    private static final String FALLBACK_DIR = "data";

    private static volatile String cachedRoot;

    private PathUtils() {
    }

    /**
     * 获取文件存储根路径（绝对路径，不含末尾分隔符）。
     *
     * @return 一定非 null，且目录已存在
     */
    public static String getClassLoadRootPath() {
        String root = cachedRoot;
        if (root == null) {
            synchronized (PathUtils.class) {
                root = cachedRoot;
                if (root == null) {
                    root = resolveRoot();
                    ensureDirExists(root);
                    cachedRoot = root;
                }
            }
        }
        return root;
    }

    private static String resolveRoot() {
        // 1) 显式配置优先
        String configured = System.getProperty(PROP_KEY);
        if (isBlank(configured)) {
            configured = System.getenv(ENV_KEY);
        }
        if (!isBlank(configured)) {
            return new File(configured.trim()).getAbsolutePath();
        }

        // 2) 类路径根目录（IDE / exploded war）
        try {
            URL resource = PathUtils.class.getClassLoader().getResource("");
            if (resource != null) {
                String decoded = URLDecoder.decode(resource.getPath(), StandardCharsets.UTF_8.name());
                decoded = decoded.replace("/target/classes", "").replace("/build/classes", "");
                File dir = new File(decoded);
                if (dir.isDirectory()) {
                    return dir.getAbsolutePath();
                }
            }
        } catch (UnsupportedEncodingException | RuntimeException ignored) {
            // 落到兜底分支
        }

        // 3) 兜底：进程工作目录下的 ./data
        return new File(System.getProperty("user.dir"), FALLBACK_DIR).getAbsolutePath();
    }

    private static void ensureDirExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
