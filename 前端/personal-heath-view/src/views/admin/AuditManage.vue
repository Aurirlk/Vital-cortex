<template>
  <div class="audit-container">
    <div class="page-header">
      <h2>审核管理</h2>
      <p>审核用户举报的帖子</p>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div
          class="stat-card__icon"
          style="background: rgba(239, 68, 68, 0.1); color: #ef4444"
        >
          P
        </div>
        <div class="stat-card__info">
          <div class="stat-label">待审核帖子</div>
          <div class="stat-value">{{ pendingPosts.length }}</div>
        </div>
        <div class="stat-card__deco"></div>
      </div>
    </div>

    <div v-if="pendingPosts.length === 0" class="empty-state">
      暂无待审核帖子
    </div>
    <div v-else class="post-list">
      <div v-for="post in pendingPosts" :key="post.id" class="post-card">
        <div class="post-card__head">
          <div class="post-author">
            <div class="post-avatar">
              {{ (post.userName || "U").charAt(0) }}
            </div>
            <span>{{ post.userName || "匿名用户" }}</span>
          </div>
          <span class="post-time">{{ post.createTime }}</span>
        </div>
        <h4 class="post-title">{{ post.title }}</h4>
        <div class="post-actions">
          <button
            class="action-btn action-btn--success"
            @click="approvePost(post.id)"
          >
            通过
          </button>
          <button
            class="action-btn action-btn--danger"
            @click="rejectPost(post.id)"
          >
            拒绝
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
// 2026-10-12 删除「敏感词管理」Tab 及配套「敏感词总数」统计卡：
// 该 Tab 调用的 audit/sensitive-word/list|save|batchDelete 三个接口
// 在整个后端不存在（controller/service/mapper/entity 皆无）。
// 同时 07_sensitive_word.sql 描述的那个 SensitiveWordMapper.xml 也早已删除，
// 且 sensitive_word 表不在结构基线文档中 —— 即该能力从未落地。
// 保留它只会让管理员点到一个必然 404 的页面，故整体摘除。
// 相关 SQL 脚本已移入 Data/90_归档/。
// 唯一保留的 Tab「帖子审核」对应的 post/reports/* 接口是真实存在的。
import request from "@/utils/request.js";

export default {
  name: "AuditManage",
  data() {
    return {
      pendingPosts: [],
    };
  },
  created() {
    this.loadPendingPosts();
  },
  methods: {
    async loadPendingPosts() {
      try {
        const { data } = await request.get("post/reports/pending");
        if (data.code === 200) this.pendingPosts = data.data;
      } catch (e) {
        console.error(e);
      }
    },
    async approvePost(id) {
      try {
        await request.post("post/reports/handle", null, {
          params: { id, status: 1 },
        });
        this.loadPendingPosts();
        this.$message.success("已通过");
      } catch (e) {
        console.error(e);
      }
    },
    async rejectPost(id) {
      try {
        await request.post("post/reports/handle", null, {
          params: { id, status: 2 },
        });
        this.loadPendingPosts();
        this.$message.success("已拒绝");
      } catch (e) {
        console.error(e);
      }
    },
  },
};
</script>

<style scoped>
.audit-container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}
.page-header {
  margin-bottom: 24px;
}
.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: #191c1e;
  margin: 0 0 8px;
}
.page-header p {
  font-size: 14px;
  color: #647084;
  margin: 0;
}
.stats-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  margin-bottom: 24px;
}
.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  border: 1px solid #e6ebf2;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}
.stat-card__icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 800;
}
.stat-card__info {
  flex: 1;
}
.stat-label {
  font-size: 13px;
  color: #647084;
  font-weight: 600;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 28px;
  font-weight: 800;
  color: #191c1e;
}
.stat-card__deco {
  position: absolute;
  right: -16px;
  top: -16px;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(0, 80, 203, 0.03);
}

.action-btn {
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}
.action-btn--success {
  background: linear-gradient(135deg, #10b981, #34d399);
  color: #fff;
}
.action-btn--danger {
  background: linear-gradient(135deg, #ef4444, #f87171);
  color: #fff;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.post-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e6ebf2;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
  transition: all 0.2s;
}
.post-card:hover {
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
}
.post-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.post-author {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #191c1e;
  font-weight: 500;
}
.post-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0050cb, #0066ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 13px;
}
.post-time {
  font-size: 12px;
  color: #647084;
}
.post-title {
  font-size: 16px;
  font-weight: 600;
  color: #191c1e;
  margin: 0 0 16px;
  line-height: 1.5;
}
.post-actions {
  display: flex;
  gap: 10px;
}
.empty-state {
  text-align: center;
  padding: 60px;
  color: #647084;
  font-size: 14px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e6ebf2;
}
</style>
