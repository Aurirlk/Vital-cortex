<template>
  <div class="agent-history">
    <div class="ah-head">
      <span class="ah-head__title">历史会话</span>
      <div class="ah-head__tools">
        <el-button link size="small" @click="reload" :loading="loading">刷新</el-button>
        <el-button link size="small" type="danger" :disabled="!list.length" @click="clearAll">
          清空
        </el-button>
      </div>
    </div>

    <div class="ah-body">
      <div v-if="loading && !list.length" class="ah-empty">加载中…</div>
      <div v-else-if="!list.length" class="ah-empty">
        还没有历史会话。提问一次后，这里会记录会话。
      </div>

      <div
        v-for="s in list"
        :key="s.id"
        class="ah-item"
        :class="{ 'is-active': activeId === String(s.id) }"
        @click="choose(s)"
      >
        <div class="ah-item__main">
          <div class="ah-item__title">{{ s.title || "未命名会话" }}</div>
          <div class="ah-item__meta">
            {{ s.message_count || 0 }} 条 · {{ fmtTime(s.last_message_time || s.create_time) }}
          </div>
        </div>
        <div class="ah-item__ops">
          <el-tooltip content="重命名" placement="top">
            <el-icon @click.stop="rename(s)"><EditPen /></el-icon>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-icon class="is-danger" @click.stop="remove(s)"><Delete /></el-icon>
          </el-tooltip>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from "element-plus";
import { EditPen, Delete } from "@element-plus/icons-vue";
import request from "@/utils/request.js";
import doctorRequest from "@/utils/doctorRequest.js";

export default {
  name: "AgentHistory",
  components: { EditPen, Delete },
  props: {
    /** 页面名是否属于医生端（决定用哪套 token 与 401 行为） */
    isDoctor: { type: Boolean, default: false },
    /** 数据智能体基础路径，如 /user/data-agent 或 /doctor/data-agent */
    baseUrl: { type: String, required: true },
  },
  emits: ["select"],
  data() {
    return { list: [], loading: false, activeId: "" };
  },
  computed: {
    http() {
      return this.isDoctor ? doctorRequest : request;
    },
  },
  created() {
    this.reload();
  },
  methods: {
    /** 兼容 Result<T> 信封（{code,msg,data}）与裸数组两种返回。 */
    unwrap(res) {
      const body = res && res.data;
      if (body && typeof body === "object" && "data" in body) return body.data;
      return body;
    },

    async reload() {
      this.loading = true;
      try {
        const res = await this.http.get(this.baseUrl + "/sessions");
        const data = this.unwrap(res);
        this.list = Array.isArray(data) ? data : [];
      } catch (e) {
        this.list = [];
      } finally {
        this.loading = false;
      }
    },

    async choose(session) {
      this.activeId = String(session.id);
      let messages = [];
      try {
        const res = await this.http.get(this.baseUrl + "/sessions/" + session.id + "/messages");
        const data = this.unwrap(res);
        messages = Array.isArray(data) ? data : [];
      } catch (e) {
        messages = [];
      }
      this.$emit("select", { id: String(session.id), title: session.title || "", messages });
    },

    async rename(session) {
      let value;
      try {
        const r = await ElMessageBox.prompt("输入新的会话名称", "重命名", {
          inputValue: session.title || "",
          inputValidator: (v) => (v && v.trim() ? true : "名称不能为空"),
          confirmButtonText: "保存",
          cancelButtonText: "取消",
        });
        value = r.value;
      } catch (e) {
        return; // 用户取消
      }
      try {
        await this.http.put(this.baseUrl + "/sessions/" + session.id + "/rename", null, {
          params: { title: value },
        });
        session.title = value;
        ElMessage.success("已重命名");
      } catch (e) {
        ElMessage.error("重命名失败");
      }
    },

    async remove(session) {
      try {
        await ElMessageBox.confirm("删除后该会话及其消息不可恢复，确认删除？", "删除会话", {
          type: "warning",
          confirmButtonText: "删除",
          cancelButtonText: "取消",
        });
      } catch (e) {
        return;
      }
      try {
        await this.http.delete(this.baseUrl + "/sessions/" + session.id);
        this.list = this.list.filter((s) => String(s.id) !== String(session.id));
        if (this.activeId === String(session.id)) this.activeId = "";
        ElMessage.success("已删除");
      } catch (e) {
        ElMessage.error("删除失败");
      }
    },

    async clearAll() {
      try {
        await ElMessageBox.confirm("将清空你的全部历史会话，且不可恢复，确认？", "清空历史", {
          type: "warning",
          confirmButtonText: "清空",
          cancelButtonText: "取消",
        });
      } catch (e) {
        return;
      }
      try {
        await this.http.delete(this.baseUrl + "/sessions");
        this.list = [];
        this.activeId = "";
        ElMessage.success("已清空");
      } catch (e) {
        ElMessage.error("清空失败");
      }
    },

    fmtTime(value) {
      if (!value) return "";
      const d = new Date(value);
      if (Number.isNaN(d.getTime())) return String(value);
      const pad = (n) => String(n).padStart(2, "0");
      return (
        d.getFullYear() +
        "-" +
        pad(d.getMonth() + 1) +
        "-" +
        pad(d.getDate()) +
        " " +
        pad(d.getHours()) +
        ":" +
        pad(d.getMinutes())
      );
    },
  },
};
</script>

<style scoped>
.agent-history {
  background: #fff;
  border: 1px solid #e6ecf5;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.ah-head {
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-bottom: 1px solid #eef3fa;
}
.ah-head__title {
  font-size: 14px;
  font-weight: 600;
  color: #1b2433;
}
.ah-head__tools {
  display: flex;
  align-items: center;
}
.ah-body {
  overflow-y: auto;
  max-height: calc(100vh - 300px);
  min-height: 90px;
  padding: 6px 0;
}
.ah-empty {
  color: #9aa4b2;
  font-size: 12.5px;
  line-height: 1.8;
  padding: 14px;
}
.ah-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  cursor: pointer;
  border-left: 3px solid transparent;
}
.ah-item:hover {
  background: #f7f9fc;
}
.ah-item.is-active {
  background: #f0f6ff;
  border-left-color: #2f7bf6;
}
.ah-item__main {
  min-width: 0;
  flex: 1;
}
.ah-item__title {
  font-size: 13px;
  color: #1b2433;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ah-item__meta {
  font-size: 11.5px;
  color: #98a2b3;
  margin-top: 2px;
}
.ah-item__ops {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #8b95a5;
  flex-shrink: 0;
}
.ah-item__ops .el-icon {
  font-size: 15px;
}
.ah-item__ops .el-icon:hover {
  color: #2f7bf6;
}
.ah-item__ops .el-icon.is-danger:hover {
  color: #f56c6c;
}
</style>
