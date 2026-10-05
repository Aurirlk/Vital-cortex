<template>
  <el-select
    v-model="innerValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :multiple="multiple"
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
    <el-option-group
      v-for="group in groupedOptions"
      :key="group.label"
      :label="group.label"
    >
      <el-option
        v-for="item in group.options"
        :key="item.id"
        :label="formatLabel(item)"
        :value="item.id"
      />
    </el-option-group>
    <template #empty>
      <div class="doctor-select__empty">
        {{ loading ? "搜索中..." : keyword ? "未找到匹配的医生" : "请输入关键字搜索" }}
      </div>
    </template>
  </el-select>
</template>

<script>
import request from "@/utils/request";

/**
 * 医生选择器（2026-10-03 医生/门类扩展性改造）
 *
 * 解决的问题：
 *   原实现在 AppointmentManage 用平铺 el-select 列出全部医生，医生数量增长后无法查找；
 *   FollowupManage 更是让管理员手输医生 ID（el-input-number），完全不可维护。
 *
 * 能力：
 *   - 远程搜索：输入即查，后端按姓名/账号/职称过滤
 *   - 按科室分组：el-option-group，先定位科室再选人
 *   - 级联缩减：传入 deptId 时只搜该科室医生
 *   - 展示「姓名 · 职称 · 科室」，不暴露裸 ID
 */
export default {
  name: "DoctorSelect",
  props: {
    /** v-model 绑定的医生ID（multiple 时为数组） */
    modelValue: { type: [Number, String, Array], default: null },
    /** 限定科室：传入后只搜该科室医生，实现「先选科室再选医生」 */
    deptId: { type: Number, default: null },
    multiple: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    clearable: { type: Boolean, default: true },
    placeholder: { type: String, default: "输入姓名/职称搜索医生" },
    width: { type: String, default: "100%" },
    /** 远程搜索每次返回条数上限 */
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
    /** 按科室名分组，未分配科室的归入「未分配」 */
    groupedOptions() {
      const map = {};
      this.options.forEach((item) => {
        const key = item.departmentName || "未分配科室";
        if (!map[key]) {
          map[key] = [];
        }
        map[key].push(item);
      });
      return Object.keys(map).map((label) => ({ label, options: map[label] }));
    },
  },
  watch: {
    // 科室变化时清空已选与候选，避免选到不属于当前科室的医生
    deptId() {
      this.options = [];
      this.keyword = "";
      this.$emit("update:modelValue", this.multiple ? [] : null);
    },
  },
  created() {
    // 回显场景：已有值时先把该项查出来，否则只显示裸 ID
    if (this.modelValue != null && this.modelValue !== "") {
      this.loadInitial();
    } else {
      this.handleSearch("");
    }
  },
  methods: {
    formatLabel(item) {
      const parts = [item.name];
      if (item.title) parts.push(item.title);
      if (item.departmentName) parts.push(item.departmentName);
      return parts.join(" · ");
    },
    async loadInitial() {
      const ids = this.multiple ? this.modelValue : [this.modelValue];
      if (!ids || ids.length === 0) return;
      this.loading = true;
      try {
        // 逐项回显（数量可控，多选场景通常不超过个位数）
        const list = [];
        for (const id of ids) {
          const { data } = await request.get(`appointment/doctor/${id}`);
          if (data && data.code === 200 && data.data) {
            list.push(data.data);
          }
        }
        this.options = list;
      } catch (e) {
        console.error("[DoctorSelect] 回显医生信息失败:", e);
      } finally {
        this.loading = false;
      }
    },
    async handleSearch(keyword) {
      this.keyword = keyword || "";
      this.loading = true;
      try {
        const { data } = await request.get("appointment/doctors/search", {
          params: {
            keyword: this.keyword,
            departmentId: this.deptId,
            limit: this.limit,
          },
        });
        if (data && data.code === 200) {
          this.options = data.data || [];
        }
      } catch (e) {
        console.error("[DoctorSelect] 搜索医生失败:", e);
        this.options = [];
      } finally {
        this.loading = false;
      }
    },
    handleChange(val) {
      // 多选时回传完整对象数组，单选回传单个对象，便于父组件直接展示
      if (this.multiple) {
        const picked = this.options.filter((o) => (val || []).includes(o.id));
        this.$emit("change", picked);
      } else {
        const picked = this.options.find((o) => o.id === val) || null;
        this.$emit("change", picked);
      }
    },
    handleClear() {
      this.options = [];
      this.handleSearch("");
      this.$emit("change", this.multiple ? [] : null);
    },
  },
};
</script>

<style scoped>
.doctor-select__empty {
  padding: 10px 0;
  text-align: center;
  font-size: 13px;
  color: #909399;
}
</style>
