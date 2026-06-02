package cn.kmbeast.service;

import cn.kmbeast.pojo.vo.UserHealthVO;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * PDF健康报告生成服务
 */
@Slf4j
@Service
public class PdfReportService {

    @Resource
    private ChartService chartService;

    /**
     * 生成健康报告PDF
     *
     * @param userName      用户名
     * @param healthData    健康数据 Map<指标名, Map<日期列表, 数值列表>>
     * @param abnormalItems 异常指标列表
     * @return PDF字节数组
     */
    public byte[] generateHealthReport(String userName,
                                        Map<String, Map<String, Object>> healthData,
                                        List<Map<String, Object>> abnormalItems) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // 设置中文字体
            PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
            document.setFont(font);

            // 1. 标题
            Paragraph title = new Paragraph("智康云 - 个人健康报告")
                    .setFontSize(24)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(title);

            // 用户信息和时间
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            Paragraph userInfo = new Paragraph("用户: " + userName + "  |  生成时间: " + now)
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(new DeviceGray(0.5f));
            document.add(userInfo);

            document.add(new Paragraph("\n"));

            // 2. 健康指标概览
            document.add(new Paragraph("一、健康指标概览").setFontSize(18).setBold());
            document.add(new Paragraph("\n"));

            // 生成图表
            if (healthData != null && !healthData.isEmpty()) {
                for (Map.Entry<String, Map<String, Object>> entry : healthData.entrySet()) {
                    String metricName = entry.getKey();
                    Map<String, Object> metricData = entry.getValue();

                    List<String> dates = (List<String>) metricData.get("dates");
                    List<Double> values = (List<Double>) metricData.get("values");

                    if (dates != null && values != null && !dates.isEmpty()) {
                        // 生成图表
                        byte[] chartImage = chartService.createLineChart(
                                metricName + "趋势",
                                "日期",
                                "数值",
                                dates,
                                values,
                                500,
                                300
                        );

                        if (chartImage != null) {
                            // 添加指标名称
                            document.add(new Paragraph(metricName).setFontSize(14).setBold());
                            // 添加图表图片
                            Image img = new Image(ImageDataFactory.create(chartImage));
                            img.setAutoScale(true);
                            document.add(img);
                            document.add(new Paragraph("\n"));
                        }
                    }
                }
            } else {
                document.add(new Paragraph("暂无健康数据记录").setFontColor(new DeviceGray(0.5f)));
            }

            // 3. 异常指标分析
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("二、异常指标分析").setFontSize(18).setBold());
            document.add(new Paragraph("\n"));

            if (abnormalItems != null && !abnormalItems.isEmpty()) {
                Table table = new Table(new float[]{3, 2, 2, 3});
                table.setWidth(100);

                // 表头
                table.addHeaderCell(new Cell().add(new Paragraph("指标名称").setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph("当前值").setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph("正常范围").setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph("状态").setBold()));

                for (Map<String, Object> item : abnormalItems) {
                    table.addCell(new Cell().add(new Paragraph(
                            (String) item.getOrDefault("name", "-"))));
                    table.addCell(new Cell().add(new Paragraph(
                            (String) item.getOrDefault("value", "-"))));
                    table.addCell(new Cell().add(new Paragraph(
                            (String) item.getOrDefault("range", "-"))));
                    
                    String status = (String) item.getOrDefault("status", "正常");
                    Cell statusCell = new Cell().add(new Paragraph(status));
                    if ("异常".equals(status)) {
                        statusCell.setFontColor(new DeviceRgb(255, 0, 0));
                    }
                    table.addCell(statusCell);
                }

                document.add(table);
            } else {
                document.add(new Paragraph("所有指标正常，无异常数据").setFontColor(new DeviceGray(0.5f)));
            }

            // 4. 健康建议
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("三、健康建议").setFontSize(18).setBold());
            document.add(new Paragraph("\n"));

            document.add(new Paragraph("1. 保持规律作息，每天保证7-8小时睡眠"));
            document.add(new Paragraph("2. 均衡饮食，多吃蔬菜水果，控制盐糖摄入"));
            document.add(new Paragraph("3. 适量运动，每周至少150分钟中等强度运动"));
            document.add(new Paragraph("4. 定期监测健康指标，及时发现问题"));
            document.add(new Paragraph("5. 保持良好心态，学会释放压力"));

            // 5. 免责声明
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("免责声明").setFontSize(14).setBold());
            document.add(new Paragraph(
                    "本报告由AI健康管理系统自动生成，仅供参考。报告中的分析和建议不能替代专业医生的诊断和治疗建议。" +
                    "如有健康问题，请及时咨询专业医生。")
                    .setFontSize(10)
                    .setFontColor(new DeviceGray(0.5f)));

            document.close();
            log.info("PDF健康报告生成成功: user={}, size={}bytes", userName, baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("生成PDF健康报告失败: {}", e.getMessage(), e);
            return null;
        }
    }
}
