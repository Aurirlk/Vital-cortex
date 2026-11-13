<template>
  <div ref="root" class="notification-bell">
    <button
      type="button"
      class="bell-btn"
      :class="{ 'is-open': showPanel }"
      :aria-expanded="showPanel ? 'true' : 'false'"
      aria-label="消息通知"
      @click="showPanel = !showPanel"
    >
      <el-icon :size="20"><Bell /></el-icon>
      <span v-if="unreadCount > 0" class="badge">{{
        unreadCount > 99 ? "99+" : unreadCount
      }}</span>
    </button>

    <div v-if="showPanel" class="notification-panel" @click.stop>
      <div class="panel-header">
        <h3>消息通知</h3>
        <button
          type="button"
          class="read-all-btn"
          :disabled="!unreadCount"
          @click="markAllRead"
        >
          全部已读
        </button>
      </div>

      <div class="panel-body">
        <p v-if="loading" class="empty-state">加载中…</p>
        <div v-else-if="notifications.length === 0" class="empty-state">
          <el-icon :size="30"><Bell /></el-icon>
          <p>暂无消息</p>
        </div>
        <div v-else class="notification-list">
          <div
            v-for="item in notifications"
            :key="item.id"
            class="notification-item"
            :class="{ unread: !item.isRead }"
            @click="handleClick(item)"
          >
            <div class="item-title">{{ item.title || "系统通知" }}</div>
            <div v-if="item.content" class="item-content">
              {{ item.content }}
            </div>
            <div class="item-time">{{ formatTime(item.createTime) }}</div>
          </div>
        </div>
      </div>

      <div v-if="allPath" class="panel-footer">
        <button type="button" class="all-btn" @click="goAll">查看全部</button>
      </div>
    </div>
  </div>
</template>

<script>
import request from "@/utils/request.js";
import { addWsListener, removeWsListener } from "@/utils/ws.js";
import { Bell } from "@element-plus/icons-vue";

/** 面板里最多展示多少条 —— 后端 /notification/list 返回全量，不截断会越拉越长 */
const MAX_ITEMS = 20;

/**
 * 通知铃。
 *
 * <p>此前这个组件是「半成品」：标题、按钮、空状态、图标全是空的
 * （<code>&lt;h3&gt;&lt;/h3&gt;</code>、<code>&lt;span class="bell-icon"&gt;&lt;/span&gt;</code>），
 * 时间格式化只返回数字（"5" 而不是 "5 分钟前"），点了通知也不会跳转，
 * 因此一直没有被任何布局引用。这里补齐文案与交互，并接到
 * 用户端顶栏与管理端顶栏。</p>
 *
 * <p>数据走 <code>/notification/*</code> 而不是 <code>/message/query</code>：
 * 后者是分页接口，未传 size 时默认只返回 10 条，
 * 用它算未读数会在超过 10 条时永远显示「10」。</p>
 */
export default {
  name: "NotificationBell",
  components: { Bell },
  props: {
    /**
     * 「查看全部」的落地页。为空字符串时不显示该入口。
     * 用户端是 /message，管理端是 /admin/messageManage。
     */
    allPath: {
      type: String,
      default: "/message",
    },
  },
  data() {
    return {
      showPanel: false,
      loading: false,
      unreadCount: 0,
      notifications: [],
    };
  },
  created() {
    this.loadUnreadCount();
    this.loadNotifications();
    addWsListener("notification", this.onNotification);
  },
  mounted() {
    document.addEventListener("click", this.onDocumentClick);
    document.addEventListener("keydown", this.onKeydown);
  },
  beforeUnmount() {
    removeWsListener("notification", this.onNotification);
    document.removeEventListener("click", this.onDocumentClick);
    document.removeEventListener("keydown", this.onKeydown);
  },
  methods: {
    onNotification(data) {
      if (!data) return;
      this.unreadCount++;
      if (data.id != null) {
        // 同一通知可能被重复推送（重连/补发），按 id 去重
        const exists = this.notifications.some((n) => n.id === data.id);
        if (!exists) this.notifications.unshift(data);
      }
      if (this.notifications.length > MAX_ITEMS) {
        this.notifications = this.notifications.slice(0, MAX_ITEMS);
      }
    },
    /** 点击面板与铃铛之外的区域时收起 —— 否则面板会一直挡在页面上 */
    onDocumentClick(e) {
      if (!this.showPanel) return;
      const root = this.$refs.root;
      if (root && !root.contains(e.target)) this.showPanel = false;
    },
    onKeydown(e) {
      if (e.key === "Escape" && this.showPanel) this.showPanel = false;
    },
    async loadUnreadCount() {
      try {
        const { data } = await request.get("notification/unread");
        if (data.code === 200) this.unreadCount = Number(data.data) || 0;
      } catch (e) {
        console.warn("[NotificationBell] 未读数加载失败:", e && e.message);
      }
    },
    async loadNotifications() {
      this.loading = true;
      try {
        const { data } = await request.get("notification/list");
        if (data.code === 200) {
          const list = Array.isArray(data.data) ? data.data : [];
          this.notifications = list.slice(0, MAX_ITEMS);
        }
      } catch (e) {
        console.warn("[NotificationBell] 通知列表加载失败:", e && e.message);
      } finally {
        this.loading = false;
      }
    },
    async markAllRead() {
      if (!this.unreadCount) return;
      try {
        const { data } = await request.post("notification/readAll");
        if (data.code === 200) {
          this.unreadCount = 0;
          this.notifications.forEach((n) => {
            n.isRead = 1;
          });
        }
      } catch (e) {
        console.warn("[NotificationBell] 全部已读失败:", e && e.message);
      }
    },
    async handleClick(item) {
      if (!item.isRead) {
        try {
          const { data } = await request.post(`notification/read/${item.id}`);
          if (data.code === 200) {
            item.isRead = 1;
            this.unreadCount = Math.max(0, this.unreadCount - 1);
          }
        } catch (e) {
          console.warn("[NotificationBell] 标记已读失败:", e && e.message);
        }
      }
      // 业务通知的价值一半在「点了能到现场」，没有跳转就只是个通知墙
      const link = item.linkUrl;
      if (link) {
        this.showPanel = false;
        if (/^https?:\/\//i.test(link)) {
          window.open(link, "_blank", "noopener,noreferrer");
        } else {
          this.$router.push(link);
        }
      }
    },
    goAll() {
      this.showPanel = false;
      this.$router.push(this.allPath);
    },
    formatTime(time) {
      if (!time) return "";
      const d = new Date(String(time).replace(" ", "T"));
      if (Number.isNaN(d.getTime())) return String(time).slice(5, 16);
      const diff = Date.now() - d.getTime();
      if (diff < 0) return "刚刚";
      if (diff < 60000) return "刚刚";
      if (diff < 3600000) return Math.floor(diff / 60000) + " 分钟前";
      if (diff < 86400000) return Math.floor(diff / 3600000) + " 小时前";
      if (diff < 86400000 * 7) return Math.floor(diff / 86400000) + " 天前";
      const mm = String(d.getMonth() + 1).padStart(2, "0");
      const dd = String(d.getDate()).padStart(2, "0");
      return `${mm}-${dd}`;
    },
  },
};
</script>

<style scoped>
.notification-bell {
  position: relative;
  box-sizing: border-box;
}

.bell-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #475569;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.bell-btn:hover,
.bell-btn.is-open {
  background: #f3f4f6;
  color: #0050cb;
}

.badge {
  position: absolute;
  top: 2px;
  right: 1px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  box-sizing: border-box;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
  border-radius: 8px;
}

.notification-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 340px;
  max-height: 460px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e6ebf2;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
  z-index: 2000;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f3f8;
}

.panel-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #191c1e;
}

.read-all-btn {
  background: none;
  border: none;
  padding: 0;
  color: #0050cb;
  font-size: 12px;
  cursor: pointer;
}

.read-all-btn:disabled {
  color: #b6c0cf;
  cursor: default;
}

.panel-body {
  flex: 1 1 auto;
  min-height: 0;
  max-height: 360px;
  overflow-y: auto;
}

.notification-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f5f7fa;
  cursor: pointer;
  transition: background 0.2s ease;
}

.notification-item:hover {
  background: #f8fafc;
}

.notification-item.unread {
  background: rgba(0, 80, 203, 0.04);
}

.item-title {
  margin-bottom: 4px;
  font-size: 13.5px;
  font-weight: 600;
  color: #191c1e;
}

.notification-item.unread .item-title {
  color: #0050cb;
}

.item-content {
  margin-bottom: 4px;
  font-size: 12.5px;
  line-height: 1.5;
  color: #647084;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-time {
  font-size: 11.5px;
  color: #94a3b8;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 36px 16px;
  color: #94a3b8;
  font-size: 13px;
}

.empty-state p {
  margin: 0;
}

.panel-footer {
  padding: 10px 16px;
  border-top: 1px solid #f0f3f8;
  text-align: center;
}

.all-btn {
  border: none;
  background: none;
  color: #0050cb;
  font-size: 12.5px;
  cursor: pointer;
}

.all-btn:hover {
  text-decoration: underline;
}
</style>
