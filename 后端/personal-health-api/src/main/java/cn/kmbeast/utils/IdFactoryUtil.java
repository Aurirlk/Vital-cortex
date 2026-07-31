package cn.kmbeast.utils;

import java.util.UUID;

/**
 * 文件 ID 生成器。
 *
 * <p>MM-07 整改：原实现为 {@code UUID.randomUUID().toString().substring(1, 8)}，
 * 只保留 7 位十六进制（约 2.7e8 空间）。按生日悖论，约 1.9 万个文件即有 50% 概率碰撞；
 * 而 {@code FileController#saveFile} 遇到同名文件会主动 delete 覆盖，
 * 结果是**静默覆盖他人的体检报告**。同时 7 位空间也使匿名遍历下载成为可能。
 *
 * <p>现改为完整 UUID（去掉连字符，32 位十六进制 / 122 位有效随机），
 * 既消除碰撞风险，也让 URL 不可枚举。
 */
public class IdFactoryUtil {

    private IdFactoryUtil() {
    }

    public static String getFileId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
