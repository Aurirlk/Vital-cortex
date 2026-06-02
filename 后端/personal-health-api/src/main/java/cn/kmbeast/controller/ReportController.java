package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.vo.UserHealthVO;
import cn.kmbeast.service.AiHealthDataService;
import cn.kmbeast.service.PdfReportService;
import cn.kmbeast.service.UserHealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 健康报告接口
 */
@Slf4j
@RestController
@RequestMapping("/report")
public class ReportController {

    @Resource
    private PdfReportService pdfReportService;

    @Resource
    private UserHealthService userHealthService;

    @Resource
    private AiHealthDataService aiHealthDataService;

    @Resource
    private UserMapper userMapper;

    /**
     * 下载健康报告PDF
     */
    @Protector
    @GetMapping("/health-pdf")
    public void downloadHealthReport(HttpServletResponse response) {
        try {
            Integer userId = LocalThreadHolder.getUserId();
            
            // 获取用户信息
            User user = userMapper.getByActive(User.builder().id(userId).build());
            if (user == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"code\":400,\"message\":\"用户不存在\"}");
                return;
            }

            // 获取健康数据
            Map<String, Map<String, Object>> healthData = new LinkedHashMap<>();
            
            // 获取最近365天的健康数据
            Result<Map<String, Object>> healthRecords = aiHealthDataService.getRecentHealthRecords(userId, 365);
            if (healthRecords.getData() != null) {
                Map<String, Object> recordsData = healthRecords.getData();
                // 解析健康数据
                if (recordsData.containsKey("records")) {
                    List<Map<String, Object>> records = (List<Map<String, Object>>) recordsData.get("records");
                    // 按指标分组
                    Map<String, List<Map<String, Object>>> groupedRecords = new LinkedHashMap<>();
                    for (Map<String, Object> record : records) {
                        String name = (String) record.getOrDefault("指标名称", "未知");
                        groupedRecords.computeIfAbsent(name, k -> new ArrayList<>()).add(record);
                    }
                    
                    // 转换为图表数据格式
                    for (Map.Entry<String, List<Map<String, Object>>> entry : groupedRecords.entrySet()) {
                        List<Map<String, Object>> recordList = entry.getValue();
                        List<String> dates = new ArrayList<>();
                        List<Double> values = new ArrayList<>();
                        
                        for (Map<String, Object> record : recordList) {
                            String date = (String) record.getOrDefault("记录时间", "");
                            String value = (String) record.getOrDefault("记录值", "0");
                            try {
                                dates.add(date.length() > 10 ? date.substring(5, 10) : date);
                                values.add(Double.parseDouble(value));
                            } catch (NumberFormatException ignored) {}
                        }
                        
                        if (!dates.isEmpty()) {
                            Map<String, Object> metricData = new LinkedHashMap<>();
                            metricData.put("dates", dates);
                            metricData.put("values", values);
                            healthData.put(entry.getKey(), metricData);
                        }
                    }
                }
            }

            // 获取异常指标
            List<Map<String, Object>> abnormalItems = new ArrayList<>();
            Result<Map<String, Object>> abnormalResult = aiHealthDataService.getAbnormalIndicators(userId);
            if (abnormalResult.getData() != null) {
                Map<String, Object> abnormalData = abnormalResult.getData();
                if (abnormalData.containsKey("异常指标列表")) {
                    abnormalItems = (List<Map<String, Object>>) abnormalData.get("异常指标列表");
                }
            }

            // 生成PDF
            byte[] pdfBytes = pdfReportService.generateHealthReport(
                    user.getUserName(),
                    healthData,
                    abnormalItems
            );

            if (pdfBytes == null || pdfBytes.length == 0) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"code\":500,\"message\":\"PDF生成失败\"}");
                return;
            }

            // 设置响应头
            String filename = "health-report-" + 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + 
                    ".pdf";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);
            response.setContentLength(pdfBytes.length);

            // 写入响应
            try (OutputStream os = response.getOutputStream()) {
                os.write(pdfBytes);
                os.flush();
            }

            log.info("健康报告下载成功: userId={}, filename={}", userId, filename);

        } catch (Exception e) {
            log.error("生成健康报告失败", e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"code\":500,\"message\":\"报告生成失败: " + e.getMessage() + "\"}");
            } catch (Exception ignored) {}
        }
    }
}
