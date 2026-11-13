package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.core.nl2sql.DataAgentConfigHolder;
import cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据智能体管理（管理员）。
 *
 * <p>提供运行时开关与查询能力（关键词召回预览、表清单），供管理端「数据智能体」页面使用。
 * 全部走 {@code @Protector(role="管理员")}。</p>
 */
@Slf4j
@RestController
@RequestMapping("/data-agent/admin")
public class DataAgentAdminController {

    @Resource
    private DataAgentConfigHolder configHolder;

    @Resource
    private JdbcSchemaIntrospector introspector;

    @Resource
    private cn.kmbeast.core.nl2sql.Nl2SqlPromptBuilder promptBuilder;

    /** 当前配置快照（含说明与生效值）。 */
    @Protector(role = "管理员")
    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return ApiResult.success(configHolder.snapshot());
    }

    /** 更新配置（在线生效，无需重启）。 */
    @Protector(role = "管理员")
    @PostMapping("/config")
    public Result<Map<String, Object>> updateConfig(@RequestBody Map<String, String> body) {
        configHolder.update(body);
        return ApiResult.success(configHolder.snapshot());
    }

    /** 可被数据智能体访问的表清单（黑名单已剔除），用于管理端预览「召回范围」。 */
    @Protector(role = "管理员")
    @GetMapping("/tables")
    public Result<List<Map<String, Object>>> tables() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JdbcSchemaIntrospector.TableMeta t : introspector.listTables()) {
            if (cn.kmbeast.core.nl2sql.Nl2SqlSchemaService.isBlacklisted(t.getName())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", t.getName());
            item.put("comment", t.getComment());
            out.add(item);
        }
        return ApiResult.success(out);
    }

    /**
     * 关键词字典（指标白名单 + 口语别名）。
     *
     * <p>数据智能体的「指标型」提问完全依赖这份硬编码白名单（LLM 不产 SQL），
     * 因此管理员需要能看到它到底认哪些词。</p>
     */
    @Protector(role = "管理员")
    @GetMapping("/keywords")
    public Result<List<Map<String, Object>>> keywords() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (cn.kmbeast.crm.agent.tool.HealthMetric m : cn.kmbeast.crm.agent.tool.HealthMetric.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("metric", m.name());
            item.put("dbName", m.dbName());
            item.put("aliases", new ArrayList<>(m.aliases()));
            out.add(item);
        }
        return ApiResult.success(out);
    }

    /** 提示词模板清单（段落查询用）。 */
    @Protector(role = "管理员")
    @GetMapping("/prompts")
    public Result<List<Map<String, Object>>> prompts() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : promptBuilder.templateNames()) {
            String raw = promptBuilder.raw(name);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("lines", raw == null ? 0 : raw.split("\n", -1).length);
            item.put("chars", raw == null ? 0 : raw.length());
            out.add(item);
        }
        return ApiResult.success(out);
    }

    /** 单个提示词模板原文。 */
    @Protector(role = "管理员")
    @GetMapping("/prompts/{name}")
    public Result<Map<String, Object>> prompt(@PathVariable("name") String name) {
        // 白名单校验：只允许读取已加载的模板名，防止路径穿越读取任意资源
        if (!promptBuilder.templateNames().contains(name)) {
            return ApiResult.error("模板不存在");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("content", promptBuilder.raw(name));
        return ApiResult.success(item);
    }
}
