package cn.kmbeast.controller;

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
import java.util.*;

/**
 * 文件前端控制器
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
     * 允许的文件类型
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
            ".pdf", ".doc", ".docx", ".txt",
            ".mp4", ".avi", ".mov"
    )));

    /**
     * 文件上传
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile multipartFile) {
        try {
            String fileName = generateSafeFileName(multipartFile);
            if (saveFile(multipartFile, fileName)) {
                Map<String, String> data = new HashMap<>();
                data.put("url", "http://localhost:" + PORT + API + "/file/getFile?fileName=" + fileName);
                return ApiResult.success(data);
            }
            return ApiResult.error("文件上传失败");
        } catch (Exception e) {
            log.error("文件上传异常", e);
            return ApiResult.error("文件上传异常: " + e.getMessage());
        }
    }

    /**
     * 视频上传
     */
    @PostMapping("/video/upload")
    public Result<Map<String, String>> videoUpload(@RequestParam("file") MultipartFile multipartFile) {
        return uploadFile(multipartFile);
    }

    /**
     * 查看图片资源（修复路径穿越漏洞）
     */
    @GetMapping("/getFile")
    public void getImage(@RequestParam("fileName") String imageName,
                         HttpServletResponse response) throws IOException {
        // 1. 清理文件名，防止路径穿越
        String safeFileName = sanitizeFileName(imageName);

        // 2. 获取文件目录
        File fileDir = new File(PathUtils.getClassLoadRootPath() + "/pic");
        File image = new File(fileDir, safeFileName);

        // 3. 验证文件路径是否在允许的目录内（防止路径穿越）
        if (!image.getCanonicalPath().startsWith(fileDir.getCanonicalPath())) {
            log.warn("路径穿越攻击被拦截: {}", imageName);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "禁止访问");
            return;
        }

        // 4. 检查文件是否存在
        if (!image.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }

        // 5. 返回文件
        try (FileInputStream fis = new FileInputStream(image);
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }
    }

    /**
     * 生成安全的文件名（UUID + 原始文件名）
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
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }
        return IdFactoryUtil.getFileId() + extension;
    }

    /**
     * 清理文件名，防止路径穿越
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.replaceAll("[\\\\/]", "")
                       .replaceAll("\\.{2,}", "")
                       .replaceAll("[^a-zA-Z0-9\\-_.]", "");
    }

    /**
     * 保存文件到磁盘
     */
    private boolean saveFile(MultipartFile multipartFile, String fileName) throws IOException {
        File fileDir = new File(PathUtils.getClassLoadRootPath() + "/pic");
        if (!fileDir.exists()) {
            if (!fileDir.mkdirs()) {
                return false;
            }
        }
        File file = new File(fileDir, fileName);
        if (file.exists()) {
            if (!file.delete()) {
                return false;
            }
        }
        if (file.createNewFile()) {
            multipartFile.transferTo(file);
            return true;
        }
        return false;
    }
}
