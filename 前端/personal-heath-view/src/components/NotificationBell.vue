<template>
  <div class="notification-bell" @click="showPanel = !showPanel">
    <span class="bell-icon"></span>
    <span v-if="unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>

    <!--  -->
    <div v-if="showPanel" class="notification-panel" @click.stop>
      <div class="panel-header">
        <h3></h3>
        <button class="read-all-btn" @click="markAllRead"></button>
      </div>
      <div class="panel-body">
        <div v-if="notifications.length === 0" class="empty-state"></div>
        <div v-else class="notification-list">
          <div v-for="item in notifications" :key="item.id"
               class="notification-item" :class="{ unread: !item.isRead }"
               @click="handleClick(item)">
            <div class="item-title">{{ item.title }}</div>
            <div class="item-content">{{ item.content }}</div>
            <div class="item-time">{{ formatTime(item.createTime) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import request from '@/utils/request.js'
import { addWsListener, removeWsListener } from '@/utils/ws.js'

export default {
  name: 'NotificationBell',
  data() {
    return {
      showPanel: false,
      unreadCount: 0,
      notifications: []
    }
  },
  created() {
    this.loadUnreadCount()
    this.loadNotifications()
    addWsListener('notification', this.onNotification)
  },
  beforeUnmount() {
    removeWsListener('notification', this.onNotification)
  },
  methods: {
    onNotification(data) {
      this.unreadCount++
      this.notifications.unshift(data)
    },
    async loadUnreadCount() {
      try {
        const { data } = await request.get('notification/unread')
        if (data.code === 200) this.unreadCount = data.data
      } catch (e) { console.error(e) }
    },
    async loadNotifications() {
      try {
        const { data } = await request.get('notification/list')
        if (data.code === 200) this.notifications = data.data
      } catch (e) { console.error(e) }
    },
    async markAllRead() {
      try {
        await request.post('notification/readAll')
        this.unreadCount = 0
        this.notifications.forEach(n => n.isRead = 1)
      } catch (e) { console.error(e) }
    },
    async handleClick(item) {
      if (!item.isRead) {
        await request.post(`notification/read/${item.id}`)
        item.isRead = 1
        this.unreadCount = Math.max(0, this.unreadCount - 1)
      }
    },
    formatTime(time) {
      if (!time) return ''
      const d = new Date(time)
      const now = new Date()
      const diff = now - d
      if (diff < 60000) return ''
      if (diff < 3600000) return Math.floor(diff / 60000) + ''
      if (diff < 86400000) return Math.floor(diff / 3600000) + ''
      return d.toLocaleDateString()
    }
  }
}
</script>

<style scoped>
.notification-bell {
  position: relative;
  cursor: pointer;
}

.bell-icon {
  font-size: 20px;
}

.badge {
  position: absolute;
  top: -6px;
  right: -8px;
  min-width: 18px;
  height: 18px;
  background: #ff2442;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}

.notification-panel {
  position: absolute;
  top: 100%;
  right: 0;
  width: 360px;
  max-height: 480px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 1000;
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.read-all-btn {
  background: none;
  border: none;
  color: #ff2442;
  font-size: 13px;
  cursor: pointer;
}

.panel-body {
  max-height: 400px;
  overflow-y: auto;
}

.notification-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.2s;
}

.notification-item:hover {
  background: #f8f8f8;
}

.notification-item.unread {
  background: rgba(255, 36, 66, 0.04);
}

.item-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.item-content {
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
}

.item-time {
  font-size: 12px;
  color: #999;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}
</style>
