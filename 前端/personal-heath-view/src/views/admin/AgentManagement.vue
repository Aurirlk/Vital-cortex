<template>
  <div class="agent-manage">
    <div class="page-header">
      <h2>Agent 智能体管理</h2>
      <span class="subtitle">管理 Multi-Agent 智能路由和记忆功能</span>
    </div>

    <el-tabs v-model="activeTab">
      <!-- Agent 角色列表 -->
      <el-tab-pane label="Agent 角色" name="roles">
        <el-table :data="agentRoles" style="width: 100%" v-loading="loading">
          <el-table-column type="index" label="#" width="60" />
          <el-table-column prop="type" label="角色标识" width="160" />
          <el-table-column prop="name" label="角色名称" width="200" />
          <el-table-column prop="description" label="描述" min-width="300" show-overflow-tooltip />
          <el-table-column label="系统提示词" min-width="300">
            <template #default="{ row }">
              <el-popover placement="left" :width="500" trigger="click">
                <template #reference>
                  <el-button size="small" link>查看</el-button>
                </template>
                <pre style="white-space: pre-wrap; font-size: 12px; max-height: 400px; overflow-y: auto;">{{ row.systemPrompt }}</pre>
              </el-popover>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Agent 路由测试 -->
      <el-tab-pane label="路由测试" name="route">
        <el-card style="max-width: 600px">
          <el-form label-width="80px">
            <el-form-item label="用户消息">
              <el-input v-model="testMessage" type="textarea" :rows="4" placeholder="输入测试消息，如：我发烧了应该怎么办？" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="testRoute" :loading="testing">测试路由</el-button>
            </el-form-item>
          </el-form>
          <el-alert
            v-if="testResult"
            :title="`路由结果: ${testResult}`"
            type="success"
            show-icon
            :closable="false"
          />
        </el-card>
      </el-tab-pane>

      <!-- 用户记忆管理 -->
      <el-tab-pane label="记忆管理" name="memory">
        <el-alert title="用户偏好和长期记忆存储" description="Agent 会自动保存每个用户的偏好信息，实现跨会话个性化服务。可在此查看和管理。" type="info" show-icon :closable="false" style="margin-bottom: 20px" />
        <el-empty v-if="!memoryStats" description="暂无记忆数据" />
        <el-descriptions v-else :column="2" border>
          <el-descriptions-item label="记忆用户数">{{ memoryStats.userCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="记忆条目数">{{ memoryStats.entryCount || 0 }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
export default {
  name: "AgentManagement",
  data() {
    return {
      activeTab: "roles",
      loading: false,
      testing: false,
      agentRoles: [],
      testMessage: "我发烧了应该怎么办？",
      testResult: "",
      memoryStats: null
    }
  },
  created() {
    this.loadAgentRoles();
    this.loadMemoryStats();
  },
  methods: {
    async loadAgentRoles() {
      this.loading = true;
      try {
        const res = await this.$axios.get("/agent/roles");
        if (res.data.code === 200) {
          const data = res.data.data;
          this.agentRoles = Object.entries(data || {}).map(([key, val]) => ({
            type: key,
            ...val
          }));
        }
      } catch (e) {
        console.error("加载Agent角色失败:", e);
      } finally {
        this.loading = false;
      }
    },

    async testRoute() {
      if (!this.testMessage.trim()) return;
      this.testing = true;
      try {
        const res = await this.$axios.post("/agent/identify", { message: this.testMessage });
        if (res.data.code === 200) {
          this.testResult = res.data.data;
        } else {
          this.testResult = "路由失败: " + (res.data.msg || "");
        }
      } catch (e) {
        this.testResult = "请求失败: " + (e.response?.data?.message || e.message);
      } finally {
        this.testing = false;
      }
    },

    async loadMemoryStats() {
      try {
        const res = await this.$axios.get("/agent/memory/stats");
        if (res.data.code === 200) {
          this.memoryStats = res.data.data;
        }
      } catch (e) {
        console.error("加载记忆统计失败:", e);
      }
    }
  }
}
</script>

<style scoped>
.agent-manage {
  padding: 20px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 4px;
}

.page-header .subtitle {
  font-size: 14px;
  color: #8c8c8c;
}
</style>
