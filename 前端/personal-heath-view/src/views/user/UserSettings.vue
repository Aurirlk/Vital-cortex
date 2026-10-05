<template>
  <div class="user-settings">
    <div class="settings-header">
      <h2>设置</h2>
      <span class="subtitle">管理您的个人偏好和账号安全</span>
    </div>

    <el-tabs v-model="activeTab" tab-position="left" class="settings-tabs">
      <!-- 基本设置 -->
      <el-tab-pane label="基本设置" name="basic">
        <div class="settings-section">
          <h3>基本设置</h3>
          <el-form :model="basicForm" label-width="120px" class="settings-form">
            <el-form-item label="昵称">
              <el-input v-model="basicForm.userName" placeholder="请输入昵称" maxlength="30" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="basicForm.userEmail" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="basicForm.userAccount" disabled />
              <span class="form-tip">手机号为登录账号，不可修改</span>
            </el-form-item>
            <el-form-item label="语言">
              <el-select v-model="basicForm.language" style="width: 100%">
                <el-option label="简体中文" value="zh-CN" />
                <el-option label="English" value="en-US" />
              </el-select>
            </el-form-item>
            <el-form-item label="时区">
              <el-select v-model="basicForm.timezone" style="width: 100%">
                <el-option label="Asia/Shanghai (UTC+8)" value="Asia/Shanghai" />
                <el-option label="America/New_York (UTC-5)" value="America/New_York" />
                <el-option label="Europe/London (UTC+0)" value="Europe/London" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveBasicSettings" :loading="saving">
                保存设置
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- 安全设置 -->
      <el-tab-pane label="安全设置" name="security">
        <div class="settings-section">
          <h3>安全设置</h3>
          <el-form label-width="120px" class="settings-form">
            <el-form-item label="修改密码">
              <el-button @click="changePwdDialog = true">修改密码</el-button>
              <span class="form-tip">定期修改密码可以提高账号安全性</span>
            </el-form-item>
            <el-form-item label="登录设备管理">
              <el-button @click="viewLoginDevices">查看登录设备</el-button>
              <span class="form-tip">管理已登录的设备，可以强制下线</span>
            </el-form-item>
            <el-form-item label="账号注销">
              <el-button type="danger" @click="deactivateAccount">注销账号</el-button>
              <span class="form-tip">注销后数据将无法恢复，请谨慎操作</span>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- 通知设置 -->
      <el-tab-pane label="通知设置" name="notification">
        <div class="settings-section">
          <h3>通知设置</h3>
          <el-form :model="notificationForm" label-width="160px" class="settings-form">
            <el-form-item label="系统消息通知">
              <el-switch v-model="notificationForm.systemNotify" />
              <span class="form-tip">接收系统更新、维护等通知</span>
            </el-form-item>
            <el-form-item label="健康提醒通知">
              <el-switch v-model="notificationForm.healthRemind" />
              <span class="form-tip">接收用药提醒、体检提醒等</span>
            </el-form-item>
            <el-form-item label="AI对话通知">
              <el-switch v-model="notificationForm.aiChatNotify" />
              <span class="form-tip">接收AI助手的消息通知</span>
            </el-form-item>
            <el-form-item label="预约提醒通知">
              <el-switch v-model="notificationForm.appointmentRemind" />
              <span class="form-tip">接收预约挂号的提醒通知</span>
            </el-form-item>
            <el-form-item label="商城订单通知">
              <el-switch v-model="notificationForm.orderNotify" />
              <span class="form-tip">接收订单状态变更通知</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveNotificationSettings" :loading="saving">
                保存设置
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- 隐私设置 -->
      <el-tab-pane label="隐私设置" name="privacy">
        <div class="settings-section">
          <h3>隐私设置</h3>
          <el-form :model="privacyForm" label-width="160px" class="settings-form">
            <el-form-item label="健康数据共享">
              <el-switch v-model="privacyForm.healthDataShare" />
              <span class="form-tip">允许AI助手读取您的健康数据进行分析</span>
            </el-form-item>
            <el-form-item label="个人资料可见性">
              <el-select v-model="privacyForm.profileVisibility" style="width: 100%">
                <el-option label="所有人可见" value="public" />
                <el-option label="仅自己可见" value="private" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据导出">
              <el-button @click="exportMyData">导出我的数据</el-button>
              <span class="form-tip">导出您的所有个人数据</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="savePrivacySettings" :loading="saving">
                保存设置
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- AI助手设置 -->
      <el-tab-pane label="AI助手设置" name="ai">
        <div class="settings-section">
          <h3>AI助手设置</h3>
          <el-form :model="aiForm" label-width="160px" class="settings-form">
            <el-form-item label="默认AI角色">
              <el-select v-model="aiForm.defaultAgent" style="width: 100%">
                <el-option label="健康助手" value="general_assistant" />
                <el-option label="全科医生" value="doctor" />
                <el-option label="营养师" value="nutritionist" />
                <el-option label="心理咨询师" value="psychologist" />
                <el-option label="报告分析师" value="analyst" />
              </el-select>
            </el-form-item>
            <el-form-item label="对话风格">
              <el-select v-model="aiForm.chatStyle" style="width: 100%">
                <el-option label="专业严谨" value="professional" />
                <el-option label="亲切友好" value="friendly" />
                <el-option label="简洁明了" value="concise" />
              </el-select>
            </el-form-item>
            <el-form-item label="联网搜索">
              <el-switch v-model="aiForm.enableWebSearch" />
              <span class="form-tip">允许AI助手联网搜索最新信息</span>
            </el-form-item>
            <el-form-item label="知识库检索">
              <el-switch v-model="aiForm.enableKnowledgeBase" />
              <span class="form-tip">允许AI助手检索健康知识库</span>
            </el-form-item>
            <el-form-item label="上下文轮数">
              <el-slider v-model="aiForm.contextRounds" :min="1" :max="20" :step="1" show-input />
              <span class="form-tip">保留的历史对话轮数，越多消耗token越多</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveAiSettings" :loading="saving">
                保存设置
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="changePwdDialog" title="修改密码" width="420px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="原密码">
          <el-input v-model="pwdForm.oldPwd" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPwd" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="pwdForm.againPwd" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changePwdDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePwd">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import request from "@/utils/request.js";
import { clearToken } from "@/utils/storage.js";

export default {
  name: "UserSettings",
  data() {
    return {
      activeTab: "basic",
      saving: false,
      changePwdDialog: false,
      
      // 基本设置
      basicForm: {
        userName: "",
        userEmail: "",
        userAccount: "",
        language: "zh-CN",
        timezone: "Asia/Shanghai",
      },
      
      // 通知设置
      notificationForm: {
        systemNotify: true,
        healthRemind: true,
        aiChatNotify: true,
        appointmentRemind: true,
        orderNotify: true,
      },
      
      // 隐私设置
      privacyForm: {
        healthDataShare: true,
        profileVisibility: "private",
      },
      
      // AI助手设置
      aiForm: {
        defaultAgent: "general_assistant",
        chatStyle: "friendly",
        enableWebSearch: true,
        enableKnowledgeBase: true,
        contextRounds: 10,
      },
      
      // 修改密码
      pwdForm: {
        oldPwd: "",
        newPwd: "",
        againPwd: "",
      },
    };
  },
  created() {
    this.loadUserInfo();
    this.loadSettings();
  },
  methods: {
    async loadUserInfo() {
      try {
        const { data } = await request.get("user/info");
        if (data.code === 200 && data.data) {
          const user = data.data;
          this.basicForm.userName = user.userName || "";
          this.basicForm.userEmail = user.userEmail || "";
          this.basicForm.userAccount = user.userAccount || "";
        }
      } catch (error) {
        console.error("加载用户信息失败:", error);
      }
    },
    
    async loadSettings() {
      try {
        const { data } = await request.get("user/settings");
        if (data.code === 200 && data.data) {
          const settings = data.data;
          if (settings.notification) {
            this.notificationForm = { ...this.notificationForm, ...settings.notification };
          }
          if (settings.privacy) {
            this.privacyForm = { ...this.privacyForm, ...settings.privacy };
          }
          if (settings.ai) {
            this.aiForm = { ...this.aiForm, ...settings.ai };
          }
          if (settings.language) {
            this.basicForm.language = settings.language;
          }
          if (settings.timezone) {
            this.basicForm.timezone = settings.timezone;
          }
        }
      } catch (error) {
        console.error("加载设置失败:", error);
      }
    },
    
    async saveBasicSettings() {
      if (!this.basicForm.userName.trim()) {
        this.$message.warning("昵称不能为空");
        return;
      }
      this.saving = true;
      try {
        const { data } = await request.put("user/update", {
          userName: this.basicForm.userName,
          userEmail: this.basicForm.userEmail,
        });
        if (data.code === 200) {
          this.$message.success("基本设置保存成功");
        } else {
          this.$message.error(data.msg || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败，请稍后重试");
      } finally {
        this.saving = false;
      }
    },
    
    async saveNotificationSettings() {
      this.saving = true;
      try {
        const { data } = await request.put("user/settings", {
          notification: this.notificationForm,
        });
        if (data.code === 200) {
          this.$message.success("通知设置保存成功");
        } else {
          this.$message.error(data.msg || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败，请稍后重试");
      } finally {
        this.saving = false;
      }
    },
    
    async savePrivacySettings() {
      this.saving = true;
      try {
        const { data } = await request.put("user/settings", {
          privacy: this.privacyForm,
        });
        if (data.code === 200) {
          this.$message.success("隐私设置保存成功");
        } else {
          this.$message.error(data.msg || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败，请稍后重试");
      } finally {
        this.saving = false;
      }
    },
    
    async saveAiSettings() {
      this.saving = true;
      try {
        const { data } = await request.put("user/settings", {
          ai: this.aiForm,
        });
        if (data.code === 200) {
          this.$message.success("AI助手设置保存成功");
        } else {
          this.$message.error(data.msg || "保存失败");
        }
      } catch (e) {
        this.$message.error("保存失败，请稍后重试");
      } finally {
        this.saving = false;
      }
    },
    
    async savePwd() {
      const { oldPwd, newPwd, againPwd } = this.pwdForm;
      if (!oldPwd || !newPwd || !againPwd) {
        this.$message.warning("请填写完整密码信息");
        return;
      }
      if (newPwd !== againPwd) {
        this.$message.warning("两次输入的新密码不一致");
        return;
      }
      if (newPwd.length < 6) {
        this.$message.warning("新密码长度不能少于6位");
        return;
      }
      this.saving = true;
      try {
        const { data } = await request.put("user/updatePwd", { oldPwd, newPwd });
        if (data.code === 200) {
          this.$message.success("修改成功，请重新登录");
          this.changePwdDialog = false;
          setTimeout(() => {
            clearToken();
            this.$router.push("/login");
          }, 1200);
        } else {
          this.$message.error(data.msg || "修改失败");
        }
      } catch (e) {
        this.$message.error(e.response?.data?.msg || "修改密码失败");
      } finally {
        this.saving = false;
      }
    },
    
    viewLoginDevices() {
      this.$message.info("登录设备管理功能开发中");
    },
    
    deactivateAccount() {
      this.$swal.fire({
        title: "确认注销账号",
        text: "注销后所有数据将无法恢复，此操作不可撤销！",
        icon: "warning",
        showCancelButton: true,
        confirmButtonText: "确认注销",
        cancelButtonText: "取消",
        customClass: { confirmButton: "swal2-btn-danger" },
      }).then((result) => {
        if (result.isConfirmed) {
          this.$message.info("账号注销功能开发中");
        }
      });
    },
    
    exportMyData() {
      this.$message.info("数据导出功能开发中");
    },
  },
};
</script>

<style scoped>
.user-settings {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.settings-header {
  margin-bottom: 24px;
}

.settings-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 4px;
}

.settings-header .subtitle {
  font-size: 14px;
  color: #8c8c8c;
}

.settings-tabs {
  min-height: 600px;
}

.settings-section {
  padding: 0 24px;
}

.settings-section h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 24px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f2f5;
}

.settings-form {
  max-width: 600px;
}

.form-tip {
  display: block;
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 4px;
}
</style>
