package cn.kmbeast.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 科室实体（支持多级层级）
 *
 * <p>2026-10-03 数据模型重构：新增 {@code parentId} / {@code level} / {@code code} /
 * {@code leaderId} 等字段，支撑「科室树 + 医生列表」的前端导航结构。
 * <p>结构基线见 {@code docs/数据表结构基线-20261003.md}。
 */
@Data
public class Department {

    /** 主键ID */
    private Integer id;

    /** 父科室ID，0 表示顶级科室 */
    private Integer parentId;

    /** 层级深度，1 = 一级科室 */
    private Integer level;

    /** 科室名称 */
    private String name;

    /** 科室编码（唯一），如 CARDIO / DEPT_001 */
    private String code;

    /** 科室简介 */
    private String description;

    /** 科室封面图 */
    private String cover;

    /** 科主任（hospital_doctor.id） */
    private Integer leaderId;

    /** 科室电话 */
    private String phone;

    /** 科室位置（楼栋/楼层） */
    private String location;

    /** 科室详细介绍 */
    private String intro;

    /** 排序权重（小在前） */
    private Integer sortOrder;

    /** 状态：0停用 1启用 */
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
