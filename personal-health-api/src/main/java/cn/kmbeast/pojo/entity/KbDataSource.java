package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库数据源（本地主库 / 远端 MySQL）。
 *
 * <p>{@link #passwordCipher} 存 AES-256-GCM 密文，<b>绝不明文</b>。
 * 实体上不放 {@code password} 明文字段：一旦存在这个字段，它就会从
 * Controller 一路透传到前端 JSON 里（Jackson 会照实序列化），
 * 于是「新增数据源」接口的响应体里就带着库密码。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KbDataSource {

    /** 主键 */
    private Integer id;

    /** 数据源唯一标识 */
    private String code;

    /** 展示名 */
    private String name;

    /** 类型：local / remote */
    private String type;

    /** JDBC URL（type=remote 时有值） */
    private String jdbcUrl;

    /** 连接账号 */
    private String username;

    /** 连接密码的密文 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordCipher;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    private String remark;

    /** 最近一次连通性探测时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCheckTime;

    /** 最近一次探测是否成功（null 表示从未探测） */
    private Boolean lastCheckOk;

    /** 最近一次探测的失败原因 */
    private String lastCheckMsg;

    /** 创建时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
