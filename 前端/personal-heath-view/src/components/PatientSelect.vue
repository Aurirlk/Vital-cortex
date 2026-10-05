<template>
  <el-select
    v-model="innerValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    filterable
    remote
    reserve-keyword
    :remote-method="handleSearch"
    :loading="loading"
    :style="{ width: width }"
    @change="handleChange"
    @clear="handleClear"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="formatLabel(item)"
      :value="item.id"
    />
    <template #empty>
      <div class="patient-select__empty">
        {{ loading ? "搜索中..." : keyword ? "未找到匹配的患者" : "请输入姓名或手机号搜索" }}
      </div>
    </template>
  </el-select>
</template>

<script>
import request from "@/utils/request";

/**
 * 患者选择器（2026-10-03 医生/门类扩展性改造）
 *
 * 解决的问题：
 *   FollowupManage 原先让管理员用 el-input-number **手输患者 ID**，
 *   操作员根本无从得知某个 ID 对应谁，数据极易录错。
 *
 * 能力：
 *   - 按姓名 / 账号 / 手机号（含后4位）远程搜索
 *   - 展示「姓名 · 手机后4位」，人工可核对，不暴露裸 ID
 *   - 手机号来自 2026-10-03 新增的 user.phone 字段
 */
export default {
  name: "PatientSelect",
  props: {
    modelValue: { type: [Number, String], default: null },
    disabled: { type: Boolean, default: false },
    clearable: { type: Boolean, default: true },
    placeholder: { type: String, default: "输入姓名或手机号搜索患者" },
    width: { type: String, default: "100%" },
    limit: { type: Number, default: 20 },
  },
  emits: ["update:modelValue", "change"],
  data() {
    return {
      options: [],
      loading: false,
      keyword: "",
    };
  },
  computed: {
    innerValue: {
      get() {
        return this.modelValue;
      },
      set(v) {
        this.$emit("update:modelValue", v);
      },
    },
  },
  created() {
    if (this.modelValue != null && this.modelValue !== "") {
      this.loadInitial();
    } else {
      this.handleSearch("");
    }
  },
  methods: {
    /** 展示格式：姓名 · 手机后4位（没有手机号则显示账号） */
    formatLabel(item) {
      const name = item.userName || "未命名";
      if (item.phone) {
        return `${name} · 尾号${item.phone.slice(-4)}`;
      }
      return item.userAccount ? `${name} · ${item.userAccount}` : name;
    },
    async loadInitial() {
      this.loading = true;
      try {
        const { data } = await request.get(`user/info/${this.modelValue}`);
        if (data && data.code === 200 && data.data) {
          this.options = [data.data];
        }
      } catch (e) {
        console.error("[PatientSelect] 回显患者信息失败:", e);
      } finally {
        this.loading = false;
      }
    },
    async handleSearch(keyword) {
      this.keyword = keyword || "";
      this.loading = true;
      try {
        const { data } = await request.get("user/search", {
          params: { keyword: this.keyword, limit: this.limit },
        });
        if (data && data.code === 200) {
          this.options = data.data || [];
        }
      } catch (e) {
        console.error("[PatientSelect] 搜索患者失败:", e);
        this.options = [];
      } finally {
        this.loading = false;
      }
    },
    handleChange(val) {
      const picked = this.options.find((o) => o.id === val) || null;
      this.$emit("change", picked);
    },
    handleClear() {
      this.options = [];
      this.handleSearch("");
      this.$emit("change", null);
    },
  },
};
</script>

<style scoped>
.patient-select__empty {
  padding: 10px 0;
  text-align: center;
  font-size: 13px;
  color: #909399;
}
</style>
