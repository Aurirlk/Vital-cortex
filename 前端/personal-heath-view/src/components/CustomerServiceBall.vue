<template>
  <div class="service-container">
    <!-- 悬浮球 -->
    <div class="service-ball" @click="toggleChat" :class="{ 'is-open': showChat }">
      <div class="ball-icon">
        <span v-if="!showChat">💬</span>
        <span v-else>✕</span>
      </div>
      <div class="ball-pulse" v-if="!showChat"></div>
    </div>

    <!-- 聊天窗口 -->
    <transition name="slide-up">
      <div v-if="showChat" class="chat-window">
        <!-- 头部 -->
        <div class="chat-header">
          <div class="header-info">
            <span class="header-icon">🤖</span>
            <div>
              <div class="header-title">健康助手小健</div>
              <div class="header-status">在线</div>
            </div>
          </div>
          <div class="header-actions">
            <el-icon @click="showChat = false"><Close /></el-icon>
          </div>
        </div>

        <!-- 快捷入口 -->
        <div class="quick-actions" v-if="messages.length === 0">
          <div class="quick-title">常见问题</div>
          <div class="quick-list">
            <div class="quick-item" @click="sendQuick('感冒了怎么办？')">
              <span>🤧</span> 感冒咨询
            </div>
            <div class="quick-item" @click="sendQuick('推荐一些感冒药')">
              <span>💊</span> 药品查询
            </div>
            <div class="quick-item" @click="sendQuick('高血压的预防方法')">
              <span>📚</span> 健康知识
            </div>
            <div class="quick-item" @click="sendQuick('推荐一个全科医生')">
              <span>🩺</span> 推荐医生
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div class="message-list" ref="messageList">
          <div v-if="messages.length === 0" class="welcome-msg">
            <div class="welcome-avatar">🤖</div>
            <div class="welcome-text">
              您好！我是健康助手小健，可以帮您解答健康问题、查询药品信息、推荐AI医生。请问有什么可以帮您？
            </div>
          </div>
          <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
            <div class="msg-avatar">
              <span v-if="msg.role === 'user'">👤</span>
              <span v-else>🤖</span>
            </div>
            <div class="msg-content">
              <div class="msg-text" v-html="formatMessage(msg.content)"></div>
              <div class="msg-time">{{ msg.time }}</div>
            </div>
          </div>
          <div v-if="loading" class="msg assistant">
            <div class="msg-avatar">🤖</div>
            <div class="msg-content">
              <div class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <el-input
            v-model="inputMsg"
            placeholder="请输入您的问题..."
            @keyup.enter="sendMessage"
            :disabled="loading"
            size="small"
          />
          <el-button 
            type="primary" 
            @click="sendMessage" 
            :loading="loading"
            :disabled="!inputMsg.trim()"
            size="small"
          >
            发送
          </el-button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { getToken } from "@/utils/storage.js";

export default {
  name: "CustomerServiceBall",
  data() {
    return {
      showChat: false,
      inputMsg: "",
      messages: [],
      loading: false,
      sessionId: null,
    };
  },
  methods: {
    toggleChat() {
      this.showChat = !this.showChat;
      if (this.showChat && this.messages.length > 0) {
        this.$nextTick(() => this.scrollToBottom());
      }
    },
    sendQuick(question) {
      this.inputMsg = question;
      this.sendMessage();
    },
    async sendMessage() {
      const msg = this.inputMsg.trim();
      if (!msg || this.loading) return;

      this.messages.push({
        role: "user",
        content: msg,
        time: this.formatTime(new Date()),
      });
      this.inputMsg = "";
      this.loading = true;
      this.scrollToBottom();

      try {
        const token = getToken();
        const response = await fetch(
          "http://localhost:21090/api/personal-health/v1.0/user/chat/stream",
          {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({
              message: msg,
              sessionId: this.sessionId,
            }),
          }
        );

        if (!response.ok) {
          throw new Error("请求失败");
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        let assistantMsg = {
          role: "assistant",
          content: "",
          time: this.formatTime(new Date()),
        };
        this.messages.push(assistantMsg);

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop();

          for (const line of lines) {
            if (line.startsWith("event: ")) {
              const eventName = line.substring(7).trim();
              continue;
            }
            if (line.startsWith("data: ")) {
              const data = line.substring(6);
              try {
                const json = JSON.parse(data);
                if (json.content) {
                  assistantMsg.content += json.content;
                  this.scrollToBottom();
                }
                if (json.sessionId) {
                  this.sessionId = json.sessionId;
                }
              } catch (e) {
                // 忽略解析错误
              }
            }
          }
        }
      } catch (e) {
        this.messages.push({
          role: "assistant",
          content: "抱歉，服务暂时不可用，请稍后再试。",
          time: this.formatTime(new Date()),
        });
        console.error("聊天请求失败:", e);
      }

      this.loading = false;
      this.scrollToBottom();
    },
    formatMessage(content) {
      if (!content) return "";
      return content
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\n/g, "<br>");
    },
    formatTime(date) {
      const h = date.getHours().toString().padStart(2, "0");
      const m = date.getMinutes().toString().padStart(2, "0");
      return `${h}:${m}`;
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.messageList;
        if (container) {
          container.scrollTop = container.scrollHeight;
        }
      });
    },
  },
};
</script>

<style scoped>
.service-container {
  position: fixed;
  right: 30px;
  bottom: 30px;
  z-index: 9999;
}

.service-ball {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(102, 126, 234, 0.4);
  transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  position: relative;
}

.service-ball:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 25px rgba(102, 126, 234, 0.5);
}

.service-ball.is-open {
  background: linear-gradient(135deg, #ff6b6b 0%, #ee5a24 100%);
}

.ball-icon {
  font-size: 28px;
  color: #fff;
  z-index: 1;
}

.ball-pulse {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: rgba(102, 126, 234, 0.4);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(1.5);
    opacity: 0;
  }
}

.chat-window {
  position: absolute;
  bottom: 70px;
  right: 0;
  width: 360px;
  height: 500px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 32px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-status {
  font-size: 12px;
  opacity: 0.8;
}

.header-actions {
  cursor: pointer;
  opacity: 0.8;
  transition: opacity 0.2s;
}

.header-actions:hover {
  opacity: 1;
}

.quick-actions {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.quick-title {
  font-size: 13px;
  color: #999;
  margin-bottom: 12px;
}

.quick-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.quick-item {
  padding: 10px;
  background: #f5f7fa;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;
}

.quick-item:hover {
  background: #e8eaed;
  transform: translateY(-1px);
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.welcome-msg {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.welcome-avatar {
  font-size: 24px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f0f0;
  border-radius: 50%;
  flex-shrink: 0;
}

.welcome-text {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  color: #333;
}

.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.msg.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  font-size: 20px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f0f0;
  border-radius: 50%;
  flex-shrink: 0;
}

.msg.user .msg-avatar {
  background: #667eea;
}

.msg-content {
  max-width: 75%;
}

.msg-text {
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.msg.assistant .msg-text {
  background: #f5f7fa;
  border-radius: 12px 12px 12px 2px;
  color: #333;
}

.msg.user .msg-text {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px 12px 2px 12px;
  color: #fff;
}

.msg-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}

.msg.user .msg-time {
  text-align: right;
}

.typing-indicator {
  display: flex;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 12px;
  gap: 4px;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #999;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    opacity: 0.3;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

.input-area {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.9);
}
</style>
