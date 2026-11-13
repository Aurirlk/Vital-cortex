package cn.kmbeast.service;

import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * JFreeChart 图表生成服务
 */
@Slf4j
@Service
public class ChartService {

    /**
     * 生成折线图图片
     *
     * @param title    图表标题
     * @param xLabel   X轴标签
     * @param yLabel   Y轴标签
     * @param dates    X轴数据（日期）
     * @param values   Y轴数据（数值）
     * @param width    图片宽度
     * @param height   图片高度
     * @return 图片字节数组
     */
    public byte[] createLineChart(String title, String xLabel, String yLabel,
                                   List<String> dates, List<Double> values,
                                   int width, int height) {
        try {
            // 创建数据集
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            for (int i = 0; i < dates.size() && i < values.size(); i++) {
                dataset.addValue(values.get(i), "数据", dates.get(i));
            }

            // 创建图表
            JFreeChart chart = ChartFactory.createLineChart(
                    title,
                    xLabel,
                    yLabel,
                    dataset,
                    PlotOrientation.VERTICAL,
                    false,  // 不显示图例
                    true,   // 显示工具提示
                    false   // 不显示URL
            );

            // 设置样式
            configureChart(chart);

            // 转换为字节数组
            return chartToBytes(chart, width, height);
        } catch (Exception e) {
            log.error("生成折线图失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 生成柱状图图片
     */
    public byte[] createBarChart(String title, String xLabel, String yLabel,
                                  List<String> categories, List<Double> values,
                                  int width, int height) {
        try {
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            for (int i = 0; i < categories.size() && i < values.size(); i++) {
                dataset.addValue(values.get(i), "数据", categories.get(i));
            }

            JFreeChart chart = ChartFactory.createBarChart(
                    title,
                    xLabel,
                    yLabel,
                    dataset,
                    PlotOrientation.VERTICAL,
                    false,
                    true,
                    false
            );

            configureChart(chart);
            return chartToBytes(chart, width, height);
        } catch (Exception e) {
            log.error("生成柱状图失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 配置图表样式
     */
    private void configureChart(JFreeChart chart) {
        // 设置背景色
        chart.setBackgroundPaint(Color.WHITE);

        // 设置标题字体（支持中文）
        Font titleFont = new Font("Microsoft YaHei", Font.BOLD, 16);
        chart.getTitle().setFont(titleFont);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(230, 230, 230));
        plot.setDomainGridlinePaint(new Color(230, 230, 230));

        // 设置坐标轴字体
        Font axisFont = new Font("Microsoft YaHei", Font.PLAIN, 12);
        
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setLabelFont(axisFont);
        domainAxis.setTickLabelFont(axisFont);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_45);

        plot.getRangeAxis().setLabelFont(axisFont);
        plot.getRangeAxis().setTickLabelFont(axisFont);

        // 设置线条样式
        LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(102, 126, 234));
        renderer.setSeriesStroke(0, new BasicStroke(2.5f));
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesShapesFilled(0, true);
    }

    /**
     * 将图表转换为字节数组
     */
    private byte[] chartToBytes(JFreeChart chart, int width, int height) throws IOException {
        BufferedImage image = chart.createBufferedImage(width, height);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ChartUtils.writeChartAsPNG(baos, chart, width, height);
        return baos.toByteArray();
    }
}
