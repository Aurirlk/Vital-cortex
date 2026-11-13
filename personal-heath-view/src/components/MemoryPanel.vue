<template>
  <div class="memory-panel">
    <div class="mp-head">
      <div class="mp-head__text">
        <h3>AI 记忆 · 结构化偏好</h3>
        <p>只保存「键 : 值」形式的背景事实，例如「diet : 少盐」。AI 会参考它们，但不会把它们当作指令。</p>
      </div>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      class="mp-note"
      type="info"
      :closable="false"
      show-icon
      title="这里没有「自定义提示词 / 人设」输入框"
      description="自由文本一旦写进系统提示词，就等同于给用户开了一个持久化的提示词注入入口。本页只接受结构化事实，且每条都会经过服务端整体校验。"
    />

    <div class="mp-switches">
      <div class="mp-switch">
        <div class="mp-switch__label">
          <strong>允许注入偏好</strong>
          <span>关闭后 AI 完全不读取这些事实</span>
        </div>
        <el-switch v-model="enabled" active-text="开" inactive-text="关" />
      </div>
      <div class="mp-switch">
        <div class="mp-switch__label">
          <strong>拒绝自动抽取</strong>
          <span>开启后系统不会从对话中自动归纳偏好</span>
        </div>
        <el-switch v-model="optOut" active-text="拒绝" inactive-text="允许" />
      </div>
    </div>

    <div v-if="limits" class="mp-limits">
      <el-tag size="small" effect="plain">最多 {{ limits.maxFacts }} 条</el-tag>
      <el-tag size="small" effect="plain">每条 ≤ {{ limits.maxValueChars }} 字</el-tag>
      <el-tag size="small" effect="plain">有效期 ≤ {{ limits.maxTtlDays }} 天</el-tag>
      <el-tag size="small" effect="plain">prompt 预算 {{ limits.tokenBudgetMin }}~{{ limits.tokenBudgetMax }} token</el-tag>
    </div>

    <div class="mp-rows">
      <div v-if="!rows.length" class="mp-empty">
        <div class="mp-empty__title">还没有记住任何偏好</div>
        <div class="mp-empty__desc">点击「新增一条」手动添加，或在下方尝试自动抽取。</div>
      </div>
      <div v-for="(row, i) in rows" :key="i" class="mp-row">
        <el-input v-model="row.key" class="mp-row__key" placeholder="key：小写字母 / 数字 / _" maxlength="32" />
        <el-input v-model="row.value" class="mp-row__value" placeholder="value：纯文本，不含换行与尖括号" maxlength="200" />
        <el-select v-model="row.kind" class="mp-row__kind" placeholder="类型">
          <el-option v-for="k in kinds" :key="k.value" :label="k.label" :value="k.value" />
        </el-select>
        <el-button class="mp-row__del" plain type="danger" @click="removeRow(i)">删除</el-button>
      </div>
    </div>

    <div class="mp-actions">
      <el-button @click="addRow">新增一条</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存全部</el-button>
      <el-button :loading="extracting" @click="extract">从对话自动抽取</el-button>
      <el-button type="danger" plain :loading="clearing" @click="clearAll">清空全部</el-button>
    </div>

    <p v-if="error" class="mp-error">{{ error }}</p>
    <p class="mp-foot">
      每条事实都可以单独删除，也可以一键清空；删除与清空只作用于当前登录账号。
    </p>
  </div>
</template>

<script>
import { ElMessage } from "element-plus";

const KEY_PATTERN = /^[a-z0-9_]{1,32}$/;

export default {
  name: "MemoryPanel",
  props: {
    // 用户端 /user/ai-memory；医生端复用同一组件时传 /doctor/ai-memory
    endpoint: { type: String, default: "/user/ai-memory" },
    // 医生端需传 doctorRequest 实例（带 doctor-token），不传则用全局用户端实例
    http: { type: [Object, Function], default: null },
  },
  data() {
    return {
      rows: [],
      enabled: true,
      optOut: false,
      limits: null,
      loading: false,
      saving: false,
      extracting: false,
      clearing: false,
      error: "",
      kinds: [
        { label: "偏好", value: "preference" },
        { label: "约束", value: "constraint" },
        { label: "目标", value: "goal" },
        { label: "背景", value: "context" },
      ],
    };
  },
  computed: {
    client() {
      return this.http || this.$axios;
    },
  },
  methods: {
    normalize(facts) {
      return (facts || []).map((f) => ({
        key: f.key || "",
        value: f.value || "",
        kind: f.kind || "preference",
        expiresAt: f.expiresAt || "",
        saved: true,
      }));
    },
    async load() {
      this.loading = true;
      this.error = "";
      try {
        const res = await this.client.get(this.endpoint);
        const body = res.data || {};
        if (body.code !== 200) {
          this.error = body.msg || "读取记忆失败";
          return;
        }
        const data = body.data || {};
        this.rows = this.normalize(data.facts);
        this.enabled = data.enabled !== false;
        this.optOut = data.optOut === true;
        this.limits = data.limits || null;
      } catch (e) {
        this.error = (e.response && e.response.data && e.response.data.msg) || "读取记忆失败，请确认已登录";
      } finally {
        this.loading = false;
      }
    },
    validateRows() {
      for (let i = 0; i < this.rows.length; i++) {
        const row = this.rows[i];
        if (!KEY_PATTERN.test(row.key)) {
          return `第 ${i + 1} 条：key 只允许 1~32 位小写字母、数字或下划线`;
        }
        if (!row.value || !row.value.trim()) {
          return `第 ${i + 1} 条：value 不能为空`;
        }
        if (row.value.length > 200 || /[<>{}\n\r]/.test(row.value)) {
          return `第 ${i + 1} 条：value 不得超过 200 字，且不能包含换行、尖括号或花括号`;
        }
      }
      return "";
    },
    addRow() {
      this.rows.push({ key: "", value: "", kind: "preference", expiresAt: "", saved: false });
    },
    async removeRow(index) {
      const row = this.rows[index];
      if (!row.saved) {
        this.rows.splice(index, 1);
        return;
      }
      try {
        const res = await this.client.delete(`${this.endpoint}/${encodeURIComponent(row.key)}`);
        if (res.data && res.data.code === 200) {
          ElMessage.success("已删除该条记忆");
          await this.load();
        } else {
          this.error = (res.data && res.data.msg) || "删除失败";
        }
      } catch (e) {
        this.error = (e.response && e.response.data && e.response.data.msg) || "删除失败";
      }
    },
    async save() {
      const invalid = this.validateRows();
      if (invalid) {
        this.error = invalid;
        return;
      }
      this.saving = true;
      this.error = "";
      try {
        const facts = this.rows.map((row) => {
          const item = { key: row.key, value: row.value, kind: row.kind };
          if (row.expiresAt) item.expiresAt = row.expiresAt;
          return item;
        });
        const res = await this.client.put(this.endpoint, {
          facts,
          enabled: this.enabled,
          optOut: this.optOut,
        });
        if (res.data && res.data.code === 200) {
          ElMessage.success("偏好已保存");
          await this.load();
        } else {
          this.error = (res.data && res.data.msg) || "保存失败";
        }
      } catch (e) {
        this.error = (e.response && e.response.data && e.response.data.msg) || "保存失败";
      } finally {
        this.saving = false;
      }
    },
    async clearAll() {
      this.clearing = true;
      this.error = "";
      try {
        const res = await this.client.delete(this.endpoint);
        if (res.data && res.data.code === 200) {
          ElMessage.success("记忆已清空");
          await this.load();
        } else {
          this.error = (res.data && res.data.msg) || "清空失败";
        }
      } catch (e) {
        this.error = (e.response && e.response.data && e.response.data.msg) || "清空失败";
      } finally {
        this.clearing = false;
      }
    },
    async extract() {
      this.extracting = true;
      this.error = "";
      try {
        const res = await this.client.post(`${this.endpoint}/extract`, {});
        if (res.data && res.data.code === 200) {
          const added = (res.data.data && res.data.data.added) || 0;
          ElMessage.success(added > 0 ? `已归纳 ${added} 条偏好` : "本次没有归纳出新的偏好");
          await this.load();
        } else {
          this.error = (res.data && res.data.msg) || "自动抽取未启用";
        }
      } catch (e) {
        this.error = (e.response && e.response.data && e.response.data.msg) || "自动抽取失败";
      } finally {
        this.extracting = false;
      }
    },
  },
};
</script>

<style scoped>
.memory-panel {
  --mp-surface: #ffffff;
  --mp-soft: #f6f8fc;
  --mp-border: #e5eaf2;
  --mp-text: #1f2a3d;
  --mp-muted: #7b8798;
  color: var(--mp-text);
  font-size: 14px;
}
/* scoped 编译后为 .dark .memory-panel[data-v-xxx]，祖先选择器不受影响 */
.dark .memory-panel {
  --mp-surface: #1b2432;
  --mp-soft: #222d3d;
  --mp-border: #334154;
  --mp-text: #e8edf5;
  --mp-muted: #97a5b8;
}
.mp-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.mp-head h3 {
  margin: 0 0 6px;
  font-size: 17px;
  font-weight: 700;
}
.mp-head p {
  margin: 0;
  color: var(--mp-muted);
  line-height: 1.6;
  font-size: 13px;
}
.mp-note {
  margin-bottom: 16px;
}
.mp-switches {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}
.mp-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--mp-border);
  border-radius: 10px;
  background: var(--mp-soft);
}
.mp-switch__label {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.mp-switch__label span {
  font-size: 12px;
  color: var(--mp-muted);
}
.mp-limits {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.mp-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.mp-empty {
  padding: 26px 16px;
  text-align: center;
  border: 1px dashed var(--mp-border);
  border-radius: 10px;
  background: var(--mp-soft);
}
.mp-empty__title {
  font-weight: 600;
  margin-bottom: 6px;
}
.mp-empty__desc {
  color: var(--mp-muted);
  font-size: 13px;
}
.mp-row {
  display: grid;
  grid-template-columns: 1.1fr 2fr 0.9fr auto;
  gap: 8px;
  align-items: center;
}
.mp-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--mp-border);
}
.mp-error {
  margin: 12px 0 0;
  color: #e2483c;
  font-size: 13px;
}
.mp-foot {
  margin: 12px 0 0;
  color: var(--mp-muted);
  font-size: 12px;
  line-height: 1.6;
}
@media (max-width: 720px) {
  .mp-switches {
    grid-template-columns: 1fr;
  }
  .mp-row {
    grid-template-columns: 1fr;
  }
}
</style>
