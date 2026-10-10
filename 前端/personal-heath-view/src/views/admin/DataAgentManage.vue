<template>
  <div class="dam">
    <div class="dam-head">
      <div>
        <h3 class="dam-head__title">数据智能体</h3>
        <p class="dam-head__desc">
          受控 NL2SQL 的运行时开关、关键词字典、召回范围与提示词段落。改动在线生效，无需重启。
        </p>
      </div>
      <el-tag :type="cfg.enabled ? 'success' : 'info'" effect="dark" size="large">
        {{ cfg.enabled ? "已开启" : "已关闭" }}
      </el-tag>
    </div>

    <el-alert
      v-if="cfg.enabled"
      class="dam-alert"
      type="warning"
      show-icon
      :closable="false"
      title="已开启受控 NL2SQL"
      description="请确认生产环境已按安全基线配置只读数据库账号，并已通过恶意 SQL 拦截测试。"
    />

    <el-tabs v-model="tab" class="dam-tabs">
      <!-- ============ 开关配置 ============ -->
      <el-tab-pane label="开关配置" name="config">
        <el-form label-width="150px" class="dam-form">
          <el-form-item label="启用数据智能体">
            <el-switch v-model="form.enabled" />
            <span class="dam-hint">关闭后，医生端与用户端的数据问答均不可用</span>
          </el-form-item>
          <el-form-item label="自动生成报告">
            <el-switch v-model="form.reportEnabled" />
            <span class="dam-hint">查询成功后追加 Markdown 分析报告</span>
          </el-form-item>
          <el-form-item label="最大返回行数">
            <el-input-number v-model="form.maxRows" :min="1" :max="2000" />
            <span class="dam-hint">建议 ≤ 200</span>
          </el-form-item>
          <el-form-item label="查询超时（秒）">
            <el-input-number v-model="form.queryTimeout" :min="1" :max="60" />
            <span class="dam-hint">建议 ≤ 10</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button>
            <el-button @click="loadConfig">重置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ 分端管理（2026-10-07 新增） ============ -->
      <el-tab-pane label="分端管理" name="audience">
        <el-alert type="info" :closable="false" show-icon class="dam-tip">
          按<b>使用者</b>分别管理数据智能体的开放范围与限流。留空表示「跟随全局」，
          覆盖值优先于全局值；配置落 <code>system_config</code>（<code>data_agent</code> 分组），10 秒后生效。
        </el-alert>

        <el-table :data="audienceRows" border size="small" class="dam-table">
          <el-table-column label="配置项" min-width="150">
            <template #default="{ row }">{{ row.label }}</template>
          </el-table-column>
          <el-table-column label="全局值" width="110" align="center">
            <template #default="{ row }">
              <span class="dam-global">{{ row.globalText }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用户端" align="center">
            <template #default="{ row }">
              <el-switch v-if="row.type === 'boolean'" v-model="row.user" />
              <el-input-number
                v-else
                v-model="row.user"
                :min="row.min"
                :max="row.max"
                size="small"
                controls-position="right"
                style="width: 110px"
              />
              <div class="dam-sub">{{ row.user === "" || row.user === null ? "跟随全局" : "" }}</div>
            </template>
          </el-table-column>
          <el-table-column label="医生端" align="center">
            <template #default="{ row }">
              <el-switch v-if="row.type === 'boolean'" v-model="row.doctor" />
              <el-input-number
                v-else
                v-model="row.doctor"
                :min="row.min"
                :max="row.max"
                size="small"
                controls-position="right"
                style="width: 110px"
              />
              <div class="dam-sub">{{ row.doctor === "" || row.doctor === null ? "跟随全局" : "" }}</div>
            </template>
          </el-table-column>
        </el-table>

        <el-alert type="warning" :closable="false" show-icon class="dam-tip" style="margin-top: 12px">
          布尔项留空＝跟随全局；数字项留空＝跟随全局。
          <b>医生端</b>查的是患者数据、且多表 JOIN 更多，通常需要更宽的行数与更长超时；
          <b>用户端</b>只查本人，建议收紧。
        </el-alert>

        <el-form class="dam-form" style="margin-top: 14px">
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveAudience">保存分端配置</el-button>
            <el-button @click="loadConfig">重置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ 上下文压缩（2026-10-07 新增） ============ -->
      <el-tab-pane label="上下文压缩" name="context">
        <el-alert type="info" :closable="false" show-icon class="dam-tip">
          控制 AI 问诊链路送入 prompt 的历史规模。压缩分三层：<b>条数截断</b>（保留最近 N 轮）
          → <b>字符预算</b>（超出从最旧整条丢弃）→ <b>滚动摘要</b>（丢弃条数达阈值时用关键词提取生成一条摘要，<b>零 LLM 调用</b>）。
        </el-alert>
        <el-form label-width="180px" class="dam-form">
          <el-form-item label="历史轮数上限">
            <el-input-number v-model="ctxForm.rounds" :min="0" :max="50" />
            <span class="dam-hint">一轮 = 用户 + 助手两条消息；0 = 不携带历史</span>
          </el-form-item>
          <el-form-item label="历史字符预算">
            <el-input-number v-model="ctxForm.maxChars" :min="500" :max="100000" :step="500" />
            <span class="dam-hint">超出时从最旧的消息整条丢弃，不做中间截断</span>
          </el-form-item>
          <el-form-item label="滚动摘要阈值">
            <el-input-number v-model="ctxForm.summaryThreshold" :min="0" :max="100" />
            <span class="dam-hint">被丢弃条数 ≥ 该值时生成一条早期对话摘要；0 = 不摘要</span>
          </el-form-item>
          <el-form-item label="压缩效果预览">
            <div class="dam-preview">
              <div>实际送入模型的历史：<b>{{ ctxForm.rounds * 2 }}</b> 条以内</div>
              <div>正文最长约 <b>{{ ctxForm.maxChars }}</b> 字符（超出的最早内容会被丢弃）</div>
              <div v-if="ctxForm.summaryThreshold > 0">
                丢弃 ≥ <b>{{ ctxForm.summaryThreshold }}</b> 条时保留一行摘要，保留主题线索
              </div>
              <div v-else class="dam-sub">摘要已关闭，更早的对话将完全丢失</div>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveContext">保存压缩配置</el-button>
            <el-button @click="loadConfig">重置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ 长短期记忆（2026-10-07 新增） ============ -->
      <el-tab-pane label="长短期记忆" name="memory">
        <el-alert type="info" :closable="false" show-icon class="dam-tip">
          「长期记忆」当前落地形态是<b>用户偏好</b>：用户通过
          <code>/agent/preferences/{userId}</code> 保存的键值对会被注入 system prompt，
          让回答贴合其习惯（如「素食」「每周三次慢跑」）。
          偏好落盘在 <code>cache/agent_workspace/memory/</code>，<b>不入数据库</b>。
        </el-alert>
        <el-form label-width="180px" class="dam-form">
          <el-form-item label="注入用户偏好">
            <el-switch v-model="memForm.enabled" />
            <span class="dam-hint">关闭后 Agent 不再读取任何用户偏好</span>
          </el-form-item>
          <el-form-item label="偏好段字符上限">
            <el-input-number v-model="memForm.maxChars" :min="0" :max="5000" :step="100" />
            <span class="dam-hint">超限时保留最新的若干条（更旧的参考价值低）</span>
          </el-form-item>
          <el-form-item label="偏好缓存用户上限">
            <el-input-number v-model="memForm.maxUsers" :min="10" :max="100000" :step="100" />
            <span class="dam-hint">LRU 淘汰上限，防止用户量增长后 OOM</span>
          </el-form-item>
          <el-form-item label="当前缓存统计">
            <div class="dam-preview">
              <span v-if="!memStats">加载中…</span>
              <span v-else>
                已缓存 <b>{{ memStats.cachedUsers }}</b> / {{ memStats.maxCachedUsers }} 个用户
                （每 30 分钟自动清理一次过期项）
              </span>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveMemory">保存记忆配置</el-button>
            <el-button @click="loadConfig">重置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ 沙箱管理（2026-10-07 新增） ============ -->
      <el-tab-pane label="沙箱管理" name="sandbox">
        <el-alert
          :type="sandboxForm.readonly ? 'success' : 'error'"
          :closable="false"
          show-icon
          class="dam-tip"
        >
          <template v-if="sandboxForm.readonly">
            ✅ <b>只读数据源已配置</b>（<code>ai_ro</code>）—— 即使 SQL 绕过词法守卫，也无法写入数据。
          </template>
          <template v-else>
            🔴 <b>只读数据源未配置</b> —— 当前 NL2SQL 走主数据源执行。
            请先执行 <code>Data/02_迁移脚本/09_create_ai_ro_user.sql</code>
            建 <code>ai_ro</code> 账号并在 <code>application-local.yml</code> 中配置。
          </template>
        </el-alert>

        <el-form label-width="180px" class="dam-form">
          <el-form-item label="结果字节上限">
            <el-input-number v-model="sandboxForm.maxBytes" :min="4096" :max="16777216" :step="4096" />
            <span class="dam-hint">
              防止 200 行 × 50 列的宽表返回数 MB JSON 挤爆 token
              （<code>max-rows</code> 只管行数，不管体积）
            </span>
          </el-form-item>
          <el-form-item label="SQL 词法守卫">
            <el-switch v-model="sandboxForm.guardEnabled" />
            <span class="dam-hint">
              禁 DML/DDL、禁黑名单表、禁子查询/CTE、禁隐式逗号连接。
              <b>生产环境不建议关闭</b>（关闭后只剩只读账号与租户重写两层防护）
            </span>
          </el-form-item>
          <el-form-item label="三层防护现状">
            <div class="dam-preview">
              <div>① 词法守卫：<b>{{ sandboxForm.guardEnabled ? "已开启" : "已关闭" }}</b></div>
              <div>② 租户隔离：<b>始终开启</b>（逐表覆盖租户列，代码内强制）</div>
              <div>③ 账号级只读：<b>{{ sandboxForm.readonly ? "已配置" : "未配置（降级为主数据源）" }}</b></div>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveSandbox">保存沙箱配置</el-button>
            <el-button @click="loadConfig">重置</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ 关键词字典 ============ -->
      <el-tab-pane label="关键词字典" name="keywords">
        <p class="dam-tip">
          指标型提问完全依赖这份硬编码白名单（大模型不产任何 SQL 片段），因此这里的词是「能问什么」的边界。
        </p>
        <el-table :data="keywords" border size="small" max-height="520" v-loading="loadingKeywords">
          <el-table-column prop="dbName" label="指标" width="150" />
          <el-table-column prop="metric" label="枚举" width="200" />
          <el-table-column label="可识别的口语/别名">
            <template #default="{ row }">
              <el-tag v-for="a in row.aliases" :key="a" size="small" effect="plain" class="dam-kw">
                {{ a }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 召回范围 ============ -->
      <el-tab-pane label="召回范围" name="tables">
        <p class="dam-tip">
          以下数据表可被数据智能体召回（敏感表已在服务端黑名单中永久剔除，不在此列）。
        </p>
        <el-table :data="tables" border size="small" max-height="520" v-loading="loadingTables">
          <el-table-column prop="name" label="表名" width="240" />
          <el-table-column prop="comment" label="说明" />
        </el-table>
      </el-tab-pane>

      <!-- ============ 预设问题 ============ -->
      <el-tab-pane label="预设问题" name="presets">
        <p class="dam-tip">
          医生端与用户端的「快捷提问」标签由此处维护，每行一条。留空则回落系统内置默认问题；
          保存后 10 秒内在两个端生效。
        </p>
        <el-form label-width="120px" class="dam-form dam-form--wide">
          <el-form-item label="医生端">
            <el-input
              v-model="presetDoctorText"
              type="textarea"
              :rows="5"
              resize="none"
              placeholder="每行一条，例如：这位患者最近的血压趋势如何？"
            />
          </el-form-item>
          <el-form-item label="用户端">
            <el-input
              v-model="presetUserText"
              type="textarea"
              :rows="5"
              resize="none"
              placeholder="每行一条，例如：我最近的血压趋势如何？"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingPresets" @click="savePresets">
              保存预设问题
            </el-button>
            <el-button @click="loadPresets">重置</el-button>
            <el-button link @click="resetPresetsToDefault">恢复内置默认</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ============ 提示词 / 段落 ============ -->
      <el-tab-pane label="提示词段落" name="prompts">
        <p class="dam-tip">
          数据智能体各环节使用的提示词模板（来源 DataAgent，Apache 2.0）。点击左侧条目查看原文。
        </p>
        <div class="dam-prompts">
          <div class="dam-prompts__list">
            <div
              v-for="p in prompts"
              :key="p.name"
              :class="['dam-prompt-item', { active: currentPrompt === p.name }]"
              @click="viewPrompt(p.name)"
            >
              <span class="dam-prompt-item__name">{{ p.name }}</span>
              <span class="dam-prompt-item__meta">{{ p.lines }} 行</span>
            </div>
          </div>
          <div class="dam-prompts__view">
            <pre v-if="promptContent" class="dam-prompt-pre">{{ promptContent }}</pre>
            <div v-else class="dam-empty">选择左侧模板查看内容</div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import request from "@/utils/request.js";

export default {
  name: "DataAgentManage",
  data() {
    return {
      tab: "config",
      saving: false,
      cfg: { enabled: false, reportEnabled: true, maxRows: 200, queryTimeout: 10 },
      form: { enabled: false, reportEnabled: true, maxRows: 200, queryTimeout: 10 },
      // 2026-10-07 新增：分端 / 压缩 / 记忆 / 沙箱
      audienceRows: [],
      ctxForm: { rounds: 10, maxChars: 4000, summaryThreshold: 0 },
      memForm: { enabled: true, maxChars: 800, maxUsers: 1000 },
      sandboxForm: { maxBytes: 524288, guardEnabled: true, readonly: false },
      memStats: null,
      keywords: [],
      loadingKeywords: false,
      tables: [],
      loadingTables: false,
      prompts: [],
      currentPrompt: "",
      promptContent: "",
      presetDoctorText: "",
      presetUserText: "",
      savingPresets: false,
    };
  },
  created() {
    this.loadConfig();
  },
  watch: {
    tab(v) {
      if (v === "keywords" && !this.keywords.length) this.loadKeywords();
      if (v === "tables" && !this.tables.length) this.loadTables();
      if (v === "prompts" && !this.prompts.length) this.loadPrompts();
      if (v === "presets") this.loadPresets();
      if (v === "memory" && !this.memStats) this.loadMemStats();
    },
  },
  methods: {
    async loadConfig() {
      try {
        const res = await request.get("/data-agent/admin/config");
        const data = (res.data && res.data.data) || {};
        const eff = data.effective || {};
        this.cfg = {
          enabled: !!eff.enabled,
          reportEnabled: !!eff["report-enabled"],
          maxRows: eff["max-rows"] || 200,
          queryTimeout: eff["query-timeout"] || 10,
        };
        this.form = { ...this.cfg };
        this.buildAudienceRows(data, eff);
        this.ctxForm = {
          rounds: eff.contextRounds != null ? eff.contextRounds : 10,
          maxChars: eff.contextMaxChars != null ? eff.contextMaxChars : 4000,
          summaryThreshold:
            eff.contextSummaryThreshold != null ? eff.contextSummaryThreshold : 0,
        };
        this.memForm = {
          enabled: eff.memoryEnabled !== false,
          maxChars: eff.memoryMaxChars != null ? eff.memoryMaxChars : 800,
          maxUsers: eff.memoryMaxUsers != null ? eff.memoryMaxUsers : 1000,
        };
        this.sandboxForm = {
          maxBytes: eff.sandboxMaxBytes != null ? eff.sandboxMaxBytes : 524288,
          guardEnabled: eff.sandboxGuardEnabled !== false,
          readonly: !!eff.sandboxReadonly,
        };
      } catch (e) {
        this.$message.error("读取数据智能体配置失败");
      }
    },

    /**
     * 由后端 snapshot（原始配置值）构建分端表格行。
     * 空串 = 跟随全局，故输入框留空。
     */
    buildAudienceRows(data, eff) {
      const pick = (key) => {
        const item = data[key];
        return item && item.value !== undefined && item.value !== null ? item.value : "";
      };
      const defs = [
        {
          key: "enabled",
          label: "开放数据智能体",
          type: "boolean",
          globalText: eff.enabled ? "开启" : "关闭",
        },
        {
          key: "report-enabled",
          label: "自动生成报告",
          type: "boolean",
          globalText: eff["report-enabled"] ? "开启" : "关闭",
        },
        {
          key: "max-rows",
          label: "最大返回行数",
          type: "number",
          min: 1,
          max: 2000,
          globalText: String(eff["max-rows"] || 200),
        },
        {
          key: "query-timeout",
          label: "SQL 超时（秒）",
          type: "number",
          min: 1,
          max: 60,
          globalText: String(eff["query-timeout"] || 10),
        },
      ];
      this.audienceRows = defs.map((d) => ({
        ...d,
        user: pick(`${d.key}-user`),
        doctor: pick(`${d.key}-doctor`),
      }));
    },

    /** 把一行的两端值转成后端要的对象（布尔空 → 不传该键，表示跟随全局）。 */
    audiencePayload() {
      const body = {};
      for (const row of this.audienceRows) {
        for (const side of ["user", "doctor"]) {
          const key = `${row.key}-${side}`;
          const val = row[side];
          if (val === "" || val === null || val === undefined) {
            continue;   // 空 = 不覆盖，沿用全局
          }
          body[key] = String(val);
        }
      }
      return body;
    },

    async saveAudience() {
      const payload = this.audiencePayload();
      if (!Object.keys(payload).length) {
        this.$message.warning("未做任何端侧修改（全部为空表示跟随全局）");
        return;
      }
      this.saving = true;
      try {
        const res = await request.post("/data-agent/admin/config", payload);
        if (res.data && res.data.code === 200) {
          this.$message.success("分端配置已保存，10 秒内生效");
          this.loadConfig();
        } else {
          this.$message.error((res.data && res.data.msg) || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存分端配置失败");
      } finally {
        this.saving = false;
      }
    },

    async saveContext() {
      await this.saveKeys({
        "context-rounds": String(this.ctxForm.rounds),
        "context-max-chars": String(this.ctxForm.maxChars),
        "context-summary-threshold": String(this.ctxForm.summaryThreshold),
      }, "上下文压缩配置");
    },

    async saveMemory() {
      await this.saveKeys({
        "memory-enabled": String(this.memForm.enabled),
        "memory-max-chars": String(this.memForm.maxChars),
        "memory-max-users": String(this.memForm.maxUsers),
      }, "长短期记忆配置");
    },

    async saveSandbox() {
      await this.saveKeys({
        "sandbox-max-bytes": String(this.sandboxForm.maxBytes),
        "sandbox-guard-enabled": String(this.sandboxForm.guardEnabled),
      }, "沙箱配置");
    },

    /** 统一的保存入口（后端端点为 POST /data-agent/admin/config，key-value 形式）。 */
    async saveKeys(body, label) {
      this.saving = true;
      try {
        const res = await request.post("/data-agent/admin/config", body);
        if (res.data && res.data.code === 200) {
          this.$message.success(`${label}已保存，10 秒内生效`);
          this.loadConfig();
        } else {
          this.$message.error((res.data && res.data.msg) || "保存失败");
        }
      } catch (e) {
        this.$message.error(`保存${label}失败`);
      } finally {
        this.saving = false;
      }
    },

    /** 记忆缓存统计（管理员专属端点，只返回数字不含具体记忆内容）。 */
    async loadMemStats() {
      try {
        const res = await request.get("/agent/memory/stats");
        this.memStats = (res.data && res.data.data) || null;
      } catch (e) {
        this.memStats = null;
      }
    },

    async saveConfig() {
      this.saving = true;
      try {
        const body = {
          enabled: String(this.form.enabled),
          "report-enabled": String(this.form.reportEnabled),
          "max-rows": String(this.form.maxRows),
          "query-timeout": String(this.form.queryTimeout),
        };
        const res = await request.post("/data-agent/admin/config", body);
        if (res.data && res.data.code === 200) {
          this.$message.success("配置已保存，10 秒内生效");
          this.cfg = { ...this.form };
        } else {
          this.$message.error((res.data && res.data.msg) || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败");
      } finally {
        this.saving = false;
      }
    },
    async loadKeywords() {
      this.loadingKeywords = true;
      try {
        const res = await request.get("/data-agent/admin/keywords");
        this.keywords = (res.data && res.data.data) || [];
      } finally {
        this.loadingKeywords = false;
      }
    },
    async loadTables() {
      this.loadingTables = true;
      try {
        const res = await request.get("/data-agent/admin/tables");
        this.tables = (res.data && res.data.data) || [];
      } finally {
        this.loadingTables = false;
      }
    },
    async loadPrompts() {
      const res = await request.get("/data-agent/admin/prompts");
      this.prompts = (res.data && res.data.data) || [];
    },
    async viewPrompt(name) {
      this.currentPrompt = name;
      const res = await request.get(`/data-agent/admin/prompts/${name}`);
      this.promptContent = (res.data && res.data.data && res.data.data.content) || "";
    },

    // ---------- 预设问题 ----------

    /** 把后端的 JSON 数组字符串安全转为「每行一条」的文本。 */
    jsonToLines(raw) {
      if (!raw) return "";
      try {
        const arr = JSON.parse(raw);
        return Array.isArray(arr) ? arr.filter((s) => s && String(s).trim()).join("\n") : "";
      } catch (e) {
        return "";
      }
    },

    /** 文本 → 去空去重的 JSON 数组字符串。 */
    linesToJson(text) {
      const list = String(text || "")
        .split("\n")
        .map((s) => s.trim())
        .filter((s) => s.length > 0);
      return JSON.stringify(Array.from(new Set(list)));
    },

    async loadPresets() {
      try {
        const res = await request.get("/data-agent/admin/config");
        const data = (res.data && res.data.data) || {};
        const d = data["preset-questions-doctor"] || {};
        const u = data["preset-questions-user"] || {};
        this.presetDoctorText = this.jsonToLines(d.value || d.defaultValue);
        this.presetUserText = this.jsonToLines(u.value || u.defaultValue);
      } catch (e) {
        this.$message.error("读取预设问题失败");
      }
    },

    resetPresetsToDefault() {
      // 清空即代表「使用内置默认」——后端在配置为空时自动回落
      this.presetDoctorText = "";
      this.presetUserText = "";
      this.$message.info("已清空，保存后将使用系统内置默认问题");
    },

    async savePresets() {
      this.savingPresets = true;
      try {
        const body = {
          "preset-questions-doctor": this.linesToJson(this.presetDoctorText),
          "preset-questions-user": this.linesToJson(this.presetUserText),
        };
        const res = await request.post("/data-agent/admin/config", body);
        if (res.data && res.data.code === 200) {
          this.$message.success("预设问题已保存，10 秒内生效");
        } else {
          this.$message.error((res.data && res.data.msg) || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败");
      } finally {
        this.savingPresets = false;
      }
    },
  },
};
</script>

<style scoped>
.dam {
  padding: 4px 2px;
}
.dam-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}
.dam-head__title {
  margin: 0 0 6px;
  font-size: 18px;
  color: #1b2433;
}
.dam-head__desc {
  margin: 0;
  font-size: 13px;
  color: #8b95a5;
  line-height: 1.6;
}
.dam-alert {
  margin-bottom: 12px;
}
.dam-form {
  max-width: 620px;
  margin-top: 8px;
}
.dam-form--wide {
  max-width: 760px;
}
.dam-hint {
  margin-left: 12px;
  font-size: 12.5px;
  color: #98a2b3;
}
.dam-tip {
  font-size: 13px;
  color: #67707e;
  background: #f7f9fc;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  padding: 10px 12px;
  margin: 8px 0 12px;
  line-height: 1.7;
}
/* ---------- 分端管理 / 压缩 / 记忆 / 沙箱（2026-10-07 新增） ---------- */
.dam-table {
  margin-top: 4px;
}
.dam-global {
  font-variant-numeric: tabular-nums;
  color: #67707e;
  font-size: 12.5px;
}
.dam-sub {
  font-size: 11.5px;
  color: #98a2b3;
  line-height: 1.4;
  margin-top: 2px;
}
.dam-preview {
  font-size: 12.5px;
  color: #67707e;
  line-height: 1.9;
  padding: 8px 12px;
  background: #f7f9fc;
  border: 1px solid #edf1f7;
  border-radius: 6px;
}
.dam-preview b {
  color: #0050cb;
  font-variant-numeric: tabular-nums;
}
.dam-tip code {
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(0, 80, 203, 0.08);
  color: #0050cb;
  font-size: 12px;
}
.dam-kw {
  margin: 2px 6px 2px 0;
}
.dam-prompts {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 14px;
  align-items: start;
}
.dam-prompts__list {
  border: 1px solid #e6ecf5;
  border-radius: 10px;
  overflow: hidden;
}
.dam-prompt-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid #f2f5fa;
  font-size: 13px;
  color: #333d4d;
}
.dam-prompt-item:last-child {
  border-bottom: none;
}
.dam-prompt-item:hover {
  background: #f7f9fc;
}
.dam-prompt-item.active {
  background: #eaf2ff;
  color: #1b6ef3;
  font-weight: 600;
}
.dam-prompt-item__meta {
  font-size: 12px;
  color: #98a2b3;
}
.dam-prompts__view {
  border: 1px solid #e6ecf5;
  border-radius: 10px;
  min-height: 300px;
  max-height: 560px;
  overflow: auto;
  background: #fbfcfe;
}
.dam-prompt-pre {
  margin: 0;
  padding: 14px 16px;
  font-size: 12.5px;
  line-height: 1.75;
  color: #333d4d;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "JetBrains Mono", Consolas, Menlo, monospace;
}
.dam-empty {
  padding: 40px;
  text-align: center;
  color: #9aa4b2;
  font-size: 13px;
}
</style>
