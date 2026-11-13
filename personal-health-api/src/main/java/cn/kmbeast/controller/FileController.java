package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.config.AiConfig;
import cn.kmbeast.config.SentinelBlockHandlers;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.utils.IdFactoryUtil;
import cn.kmbeast.utils.PathUtils;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文件前端控制器
 *
 * <p>本轮整改（MM-05 / MM-07 / ENG）：
 * <ul>
 *   <li>上传接口移出鉴权白名单并加 {@code @Protector}，杜绝匿名上传刷盘；</li>
 *   <li>文件名改用完整 UUID，消除枚举与静默覆盖；</li>
 *   <li>保存改为原子写入，不再"先 delete 再 createNewFile"；</li>
 *   <li>返回 URL 不再硬编码 {@code http://localhost:port}，改为可配置的对外基础地址；</li>
 *   <li>修正 {@code sanitizeFileName} 中写错的 {@code ..} 过滤正则。</li>
 * </ul>
 *
 * <p><b>遗留风险（已记录到交接手册）</b>：{@code /file/getFile} 仍保持匿名可访问，
 * 因为前端以 {@code <img src>} 直接引用、无法携带请求头。当前依靠 122 位随机文件名
 * 构成 capability URL。彻底方案是改为带签名与有效期的临时 URL，或前端改用带鉴权的
 * blob 拉取，需要前端配合改造，不在本次 P0 范围内。
 */
@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    @Value("${my-server.api-context-path}")
    private String API;

    @Value("${server.port}")
    private String PORT;

    /**
     * 对外暴露的基础地址。生产环境必须配置为网关/域名，例如 https://health.example.com。
     * 留空时退化为 http://localhost:{port}，仅适用于本地开发。
     */
    @Value("${my-server.public-base-url:}")
    private String publicBaseUrl;

    @Resource
    private AiConfig aiConfig;

    /** AI 映射用的 HTTP 客户端（短超时，best-effort） */
    private static final OkHttpClient AI_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    /**
     * 允许的文件类型
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
            ".pdf", ".doc", ".docx", ".txt",
            ".mp4", ".avi", ".mov"
    )));

    /**
     * 文件上传（需登录）
     */
    @Protector
    @SentinelResource(value = "file:upload",
            blockHandler = "uploadBlocked", blockHandlerClass = SentinelBlockHandlers.class)
    @PostMapping("/upload")
    public Result<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return ApiResult.error("上传文件不能为空");
        }
        try {
            String fileName = generateSafeFileName(multipartFile);
            if (saveFile(multipartFile, fileName)) {
                Map<String, String> data = new HashMap<>();
                data.put("url", buildFileUrl(fileName));
                return ApiResult.success(data);
            }
            return ApiResult.error("文件上传失败");
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("文件上传异常", e);
            return ApiResult.error("文件上传异常");
        }
    }

    /**
     * 视频上传（需登录）
     */
    @Protector
    @PostMapping("/video/upload")
    public Result<Map<String, String>> videoUpload(@RequestParam("file") MultipartFile multipartFile) {
        return uploadFile(multipartFile);
    }

    // ==================== 文件解析为 JSON（AI 辅助导入） ====================

    /**
     * 将上传的结构化文件（JSON / CSV / TSV / XLSX / TXT）解析为 JSON 数组，
     * 供药品 / 商品批量导入使用；可选 AI 映射到目标字段结构。
     *
     * @param file   上传文件
     * @param target 目标结构：drug(药品) / product(商城商品) / generic(不映射)
     * @param useAi  是否调用大模型把表头/字段映射到目标结构（缺 API Key 时自动降级为原样输出）
     */
    @Protector
    @PostMapping("/parse-to-json")
    public Result<Map<String, Object>> parseToJson(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "target", required = false) String target,
            @RequestParam(value = "ai", defaultValue = "false") boolean useAi) {
        if (file == null || file.isEmpty()) {
            return ApiResult.error("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase()
                : "";
        try {
            byte[] bytes = file.getBytes();
            List<Map<String, Object>> rows = parseStructuredFile(bytes, ext);
            if (rows.isEmpty()) {
                return ApiResult.error("未能从文件中解析出任何数据行");
            }
            boolean aiMapped = false;
            String mappedSchema = target == null || target.isEmpty() ? "generic" : target;
            if (useAi && !"generic".equals(mappedSchema)) {
                List<Map<String, Object>> mapped = aiMapRows(rows, mappedSchema);
                if (mapped != null && !mapped.isEmpty()) {
                    rows = mapped;
                    aiMapped = true;
                } else {
                    log.warn("[File] AI 映射失败或未配置 Key，使用原始解析结果: target={}", mappedSchema);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rows", rows);
            result.put("count", rows.size());
            result.put("schema", mappedSchema);
            result.put("aiMapped", aiMapped);
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("[File] 解析文件失败: {}", originalName, e);
            return ApiResult.error("文件解析失败：" + e.getMessage());
        }
    }

    /** 按扩展名解析结构化文件为行对象列表 */
    private List<Map<String, Object>> parseStructuredFile(byte[] bytes, String ext) throws IOException {
        switch (ext) {
            case ".json":
                return parseJsonFile(bytes);
            case ".csv":
                return parseDelimited(bytes, ',');
            case ".tsv":
                return parseDelimited(bytes, '\t');
            case ".xlsx":
                return parseXlsx(bytes);
            case ".txt":
                return parseTxt(bytes);
            default:
                throw new IllegalArgumentException("不支持的文件类型：" + ext + "（支持 json/csv/tsv/xlsx/txt）");
        }
    }

    private List<Map<String, Object>> parseJsonFile(byte[] bytes) {
        Object obj = JSON.parse(new String(bytes, StandardCharsets.UTF_8));
        JSONArray arr;
        if (obj instanceof JSONArray) {
            arr = (JSONArray) obj;
        } else if (obj instanceof JSONObject) {
            JSONObject jo = (JSONObject) obj;
            if (jo.getJSONArray("rows") != null) {
                arr = jo.getJSONArray("rows");
            } else if (jo.getJSONArray("data") != null) {
                arr = jo.getJSONArray("data");
            } else {
                arr = new JSONArray();
                arr.add(jo);
            }
        } else {
            throw new IllegalArgumentException("JSON 顶层必须是数组或对象");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) item);
                rows.add(row);
            } else if (item != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("value", String.valueOf(item));
                rows.add(row);
            }
        }
        return rows;
    }

    /** CSV/TSV 解析：首行作表头，支持引号包裹字段 */
    private List<Map<String, Object>> parseDelimited(byte[] bytes, char delimiter) throws IOException {
        String content = new String(bytes, StandardCharsets.UTF_8);
        // 去掉 UTF-8 BOM
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        List<List<String>> table = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                table.add(splitCsvLine(line, delimiter));
            }
        }
        return rowsFromTable(table);
    }

    private List<String> splitCsvLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == delimiter) {
                    fields.add(sb.toString().trim());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        fields.add(sb.toString().trim());
        return fields;
    }

    /** XLSX 解析：ZIP 中的 sharedStrings.xml + sheet1.xml，不依赖 POI */
    private List<Map<String, Object>> parseXlsx(byte[] bytes) throws IOException {
        List<String> shared = new ArrayList<>();
        List<List<String>> table = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("xl/sharedStrings.xml")) {
                    shared = parseSharedStrings(readEntry(zis));
                } else if (name.equals("xl/worksheets/sheet1.xml")
                        || name.matches("xl/worksheets/sheet\\d+\\.xml")) {
                    table = parseSheetXml(readEntry(zis), shared);
                    break;
                }
            }
        }
        return rowsFromTable(table);
    }

    private byte[] readEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private List<String> parseSharedStrings(byte[] xml) {
        List<String> list = new ArrayList<>();
        String s = new String(xml, StandardCharsets.UTF_8);
        int idx = 0;
        while ((idx = s.indexOf("<si>", idx)) != -1) {
            int end = s.indexOf("</si>", idx);
            if (end == -1) {
                break;
            }
            String si = s.substring(idx + 4, end);
            StringBuilder text = new StringBuilder();
            int t = 0;
            while ((t = si.indexOf("<t", t)) != -1) {
                int ts = si.indexOf('>', t);
                int te = si.indexOf("</t>", ts);
                if (ts == -1 || te == -1) {
                    break;
                }
                text.append(si, ts + 1, te);
                t = te + 4;
            }
            list.add(text.toString().replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&#10;", "\n").replace("&quot;", "\"").replace("&apos;", "'"));
            idx = end + 5;
        }
        return list;
    }

    private List<List<String>> parseSheetXml(byte[] xml, List<String> shared) {
        List<List<String>> table = new ArrayList<>();
        String s = new String(xml, StandardCharsets.UTF_8);
        int rowIdx = 0;
        while ((rowIdx = s.indexOf("<row", rowIdx)) != -1) {
            int rowEnd = s.indexOf("</row>", rowIdx);
            if (rowEnd == -1) {
                break;
            }
            String rowXml = s.substring(rowIdx, rowEnd + 6);
            List<String> cells = new ArrayList<>();
            int cIdx = 0;
            while ((cIdx = rowXml.indexOf("<c ", cIdx)) != -1) {
                int cEnd = rowXml.indexOf("</c>", cIdx);
                if (cEnd == -1) {
                    break;
                }
                String cellXml = rowXml.substring(cIdx, cEnd + 4);
                String type = "";
                int tPos = cellXml.indexOf(" t=\"");
                if (tPos != -1) {
                    type = cellXml.substring(tPos + 4, cellXml.indexOf('"', tPos + 4));
                }
                int vPos = cellXml.indexOf("<v>");
                String val = "";
                if (vPos != -1) {
                    int vEnd = cellXml.indexOf("</v>", vPos);
                    val = cellXml.substring(vPos + 3, vEnd);
                }
                if ("s".equals(type)) {
                    try {
                        val = shared.get(Integer.parseInt(val.trim()));
                    } catch (Exception ignored) {
                        val = "";
                    }
                }
                cells.add(val.trim());
                cIdx = cEnd + 4;
            }
            if (!cells.isEmpty()) {
                table.add(cells);
            }
            rowIdx = rowEnd + 6;
        }
        return table;
    }

    /** TXT：先试 JSON，再试分隔符，最后按行原样 */
    private List<Map<String, Object>> parseTxt(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        String trimmed = content.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                return parseJsonFile(bytes);
            } catch (Exception ignored) {
                // 继续按分隔符处理
            }
        }
        if (trimmed.contains("\t")) {
            try {
                return parseDelimited(bytes, '\t');
            } catch (Exception ignored) {
            }
        }
        if (trimmed.contains(",")) {
            try {
                return parseDelimited(bytes, ',');
            } catch (Exception ignored) {
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : content.split("\\r?\\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", line.trim());
            rows.add(row);
        }
        return rows;
    }

    /** 表头 + 行数据 → 行对象列表 */
    private List<Map<String, Object>> rowsFromTable(List<List<String>> table) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (table.isEmpty()) {
            return rows;
        }
        List<String> headers = table.get(0);
        for (int i = 1; i < table.size(); i++) {
            List<String> line = table.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                String header = headers.get(j);
                if (header == null || header.isEmpty()) {
                    header = "col" + (j + 1);
                }
                String value = j < line.size() ? line.get(j) : "";
                row.put(header, value);
            }
            rows.add(row);
        }
        return rows;
    }

    /** 调用大模型把行数据映射到目标字段结构（best-effort，失败返回 null） */
    private List<Map<String, Object>> aiMapRows(List<Map<String, Object>> rows, String target) {
        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }
        String fieldDef = "drug".equals(target)
                ? "name(名称), genericName(通用名), category(分类), specification(规格), manufacturer(生产厂家), price(价格,数字), unit(单位), description(说明), isOtc(是否OTC 1/0), stock(库存,数字), cover(图片URL)"
                : "name(名称), productType(类型: drug/device/health), categoryId(分类ID,数字,没有就省略), description(描述), cover(图片URL), price(价格,数字), originalPrice(原价,数字), stock(库存,数字), unit(单位), isHot(是否热销 1/0), isNew(是否新品 1/0)";
        JSONObject body = new JSONObject();
        body.put("model", aiConfig.getModel());
        body.put("temperature", 0.1);
        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", "你是数据整理助手。把用户给的数据行转换成 JSON 数组，每行是一个对象，只包含目标字段：" + fieldDef
                + "。能匹配的字段就匹配，不能匹配的省略；价格/库存等数字字段转成数字；无法确定的字段不要编造。只输出 JSON 数组，不要输出任何其他文字。");
        messages.add(sys);
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", JSON.toJSONString(rows));
        messages.add(user);
        body.put("messages", messages);
        try {
            Request request = new Request.Builder()
                    .url(aiConfig.getApiUrl())
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(body.toJSONString(), JSON_MEDIA))
                    .build();
            try (Response response = AI_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("[File] AI 映射 HTTP 失败: {}", response.code());
                    return null;
                }
                String respBody = response.body().string();
                JSONObject jo = JSON.parseObject(respBody);
                String content = jo.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
                if (content == null) {
                    return null;
                }
                // 去掉可能的 ```json 围栏
                content = content.trim();
                if (content.startsWith("```")) {
                    content = content.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
                }
                int start = content.indexOf('[');
                int end = content.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    content = content.substring(start, end + 1);
                }
                JSONArray arr = JSON.parseArray(content);
                List<Map<String, Object>> result = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) item);
                        result.add(row);
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("[File] AI 映射异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 查看图片资源（防路径穿越）
     */
    @GetMapping("/getFile")
    public void getImage(@RequestParam("fileName") String imageName,
                         HttpServletResponse response) throws IOException {
        // 1. 清理文件名，防止路径穿越
        String safeFileName = sanitizeFileName(imageName);
        if (safeFileName.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "文件名非法");
            return;
        }

        // 2. 获取文件目录
        File fileDir = new File(PathUtils.getClassLoadRootPath(), "pic");
        File image = new File(fileDir, safeFileName);

        // 3. 验证文件路径是否在允许的目录内（防止路径穿越）
        String canonicalDir = fileDir.getCanonicalPath() + File.separator;
        if (!image.getCanonicalPath().startsWith(canonicalDir)) {
            log.warn("路径穿越攻击被拦截: {}", imageName);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "禁止访问");
            return;
        }

        // 4. 检查文件是否存在
        if (!image.isFile()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }

        // 5. 返回文件
        response.setContentLengthLong(image.length());
        try (InputStream fis = Files.newInputStream(image.toPath());
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }
    }

    /**
     * 拼接对外可访问的文件 URL
     */
    private String buildFileUrl(String fileName) {
        String base = (publicBaseUrl == null || publicBaseUrl.trim().isEmpty())
                ? "http://localhost:" + PORT
                : publicBaseUrl.trim().replaceAll("/+$", "");
        return base + API + "/file/getFile?fileName=" + fileName;
    }

    /**
     * 生成安全的文件名（完整 UUID + 扩展名）
     */
    private String generateSafeFileName(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            originalName = "unknown";
        }
        // 提取文件扩展名
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex).toLowerCase();
        }
        // 验证文件类型
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型");
        }
        // MM-08 整改：扩展名可伪造，加魔数（magic bytes）校验，
        // 防上传"改了后缀的恶意文件"（如伪装成 jpg 的可执行文件）
        if (!matchesMagicBytes(file, extension)) {
            throw new IllegalArgumentException("文件内容与扩展名不符，已拒绝上传");
        }
        return IdFactoryUtil.getFileId() + extension;
    }

    /**
     * MM-08：按扩展名校验文件头魔数。
     */
    private boolean matchesMagicBytes(MultipartFile file, String extension) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = new byte[12];
            int read = in.read(head);
            if (read <= 0) return false;

            switch (extension) {
                case ".jpg":
                case ".jpeg":
                    return read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
                case ".png":
                    return read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
                case ".gif":
                    return read >= 6 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8';
                case ".bmp":
                    return read >= 2 && head[0] == 'B' && head[1] == 'M';
                case ".webp":
                    return read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                            && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
                case ".pdf":
                    return read >= 5 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F' && head[4] == '-';
                case ".doc":
                case ".docx":
                    // OLE2 (D0 CF 11 E0) 或 ZIP (PK..) 容器
                    return (read >= 4 && (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF
                            && (head[2] & 0xFF) == 0x11 && (head[3] & 0xFF) == 0xE0)
                            || (read >= 2 && head[0] == 'P' && head[1] == 'K');
                case ".txt":
                    // 文本文件：仅校验不含二进制控制字节（允许 \t \n \r）
                    for (int i = 0; i < read; i++) {
                        int b = head[i] & 0xFF;
                        if (b == 0) return false;
                        if (b < 0x20 && b != '\t' && b != '\n' && b != '\r') return false;
                    }
                    return true;
                case ".mp4":
                    return read >= 12 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
                case ".avi":
                    return read >= 4 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F';
                case ".mov":
                    return read >= 8 && head[4] == 'm' && head[5] == 'o' && head[6] == 'o' && head[7] == 'v';
                default:
                    return true;
            }
        } catch (Exception e) {
            log.warn("[File] 魔数校验读取失败: {}", extension, e);
            return false;
        }
    }

    /**
     * 清理文件名，防止路径穿越。
     *
     * <p>原实现的正则为 {@code "\\\\.\\\\."}，在 Java 字符串转义后等价于正则 {@code \\.\\.}，
     * 匹配的是「反斜杠 + 任意字符 + 反斜杠 + 任意字符」，根本没过滤到 {@code ..}。
     * 这里改为循环剔除，防止 {@code ....//} 这类"删一次还剩一个"的绕过。
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String cleaned = fileName.replace('\\', '/');
        // 循环剔除，避免 "....//" → "../" 的单次替换绕过
        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "");
        }
        // 仅保留中文、字母、数字、连字符、下划线、点号、斜杠
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9\\-_./\\u4e00-\\u9fa5]", "");
        // 去掉开头的斜杠，避免被当作绝对路径
        cleaned = cleaned.replaceAll("^/+", "");
        return cleaned;
    }

    /**
     * 保存文件到磁盘（原子写入）
     */
    private boolean saveFile(MultipartFile multipartFile, String fileName) throws IOException {
        File fileDir = new File(PathUtils.getClassLoadRootPath(), "pic");
        if (!fileDir.exists() && !fileDir.mkdirs()) {
            log.error("创建文件目录失败: {}", fileDir.getAbsolutePath());
            return false;
        }
        File target = new File(fileDir, fileName);
        File temp = File.createTempFile("upload-", ".tmp", fileDir);
        try {
            multipartFile.transferTo(temp);
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } finally {
            if (temp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }
}
