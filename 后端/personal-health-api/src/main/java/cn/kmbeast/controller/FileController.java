package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.utils.IdFactoryUtil;
import cn.kmbeast.utils.PathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

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
