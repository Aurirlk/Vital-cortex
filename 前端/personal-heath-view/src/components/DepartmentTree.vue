<template>
  <div class="department-tree">
    <el-input
      v-model="filterText"
      placeholder="搜索科室"
      clearable
      size="small"
      class="department-tree__filter"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>

    <el-tree
      ref="treeRef"
      :data="treeData"
      :props="treeProps"
      node-key="id"
      :filter-node-method="filterNode"
      :default-expanded-keys="defaultExpandedKeys"
      highlight-current
      :expand-on-click-node="false"
      class="department-tree__body"
      @node-click="handleNodeClick"
    >
      <template #default="{ data }">
        <span class="department-tree__node">
          <span class="department-tree__name">{{ data.name }}</span>
          <span v-if="data.doctorCount" class="department-tree__count">
            ({{ data.doctorCount }})
          </span>
        </span>
      </template>
    </el-tree>

    <div v-if="!treeData.length && !loading" class="department-tree__empty">
      暂无科室
    </div>
  </div>
</template>

<script>
import request from "@/utils/request";
import { Search } from "@element-plus/icons-vue";

/**
 * 科室树（2026-10-03 科室支持多级层级）
 *
 * 解决的问题：
 *   原科室表只有平铺列表（无 parent_id），科室变多后只能靠滚动查找。
 *   迁移后 department 已支持 parent_id / level / code，本组件据此渲染树形导航。
 *
 * 能力：
 *   - 关键字过滤（el-tree 的 filter-node-method）
 *   - 节点显示「科室名 (医生数)」
 *   - 支持「全部科室」虚拟根节点，便于一键查看所有医生
 */
export default {
  name: "DepartmentTree",
  props: {
    /** 是否在顶部插入「全部科室」虚拟节点 */
    showAllNode: { type: Boolean, default: true },
  },
  emits: ["select"],
  data() {
    return {
      treeData: [],
      filterText: "",
      loading: false,
      treeProps: { children: "children", label: "name" },
      defaultExpandedKeys: [],
    };
  },
  watch: {
    filterText(val) {
      this.$refs.treeRef && this.$refs.treeRef.filter(val);
    },
  },
  created() {
    this.loadTree();
  },
  methods: {
    async loadTree() {
      this.loading = true;
      try {
        const { data } = await request.get("appointment/departments/tree");
        if (data && data.code === 200) {
          const list = data.data || [];
          this.treeData = this.showAllNode
            ? [{ id: 0, name: "全部科室", doctorCount: this.sumCount(list), children: [] }, ...list]
            : list;
          // 默认展开一级科室
          this.defaultExpandedKeys = list.map((d) => d.id);
        }
      } catch (e) {
        console.error("[DepartmentTree] 加载科室树失败:", e);
        this.treeData = [];
      } finally {
        this.loading = false;
      }
    },
    sumCount(nodes) {
      let total = 0;
      const walk = (list) => {
        list.forEach((n) => {
          total += n.doctorCount || 0;
        });
      };
      walk(nodes);
      return total;
    },
    filterNode(value, data) {
      if (!value) return true;
      return data.name.indexOf(value) !== -1;
    },
    handleNodeClick(node) {
      this.$emit("select", node);
    },
    /** 供父组件调用：重新加载（新增/编辑科室后） */
    refresh() {
      this.loadTree();
    },
  },
  components: { Search },
};
</script>

<style scoped>
.department-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.department-tree__filter {
  margin-bottom: 10px;
}

.department-tree__body {
  flex: 1;
  overflow-y: auto;
}

.department-tree__node {
  display: flex;
  align-items: center;
  gap: 6px;
}

.department-tree__name {
  font-size: 14px;
}

.department-tree__count {
  font-size: 12px;
  color: #909399;
}

.department-tree__empty {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: #909399;
}
</style>
