<template>
  <div class="doctor-login">
    <!-- 左侧品牌区 -->
    <div class="doctor-login__brand">
      <div class="brand-inner">
        <div class="brand-badge">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
            <path
              d="M12 3v18M3 12h18"
              stroke="#fff"
              stroke-width="2.4"
              stroke-linecap="round"
            />
            <circle
              cx="12"
              cy="12"
              r="9.2"
              stroke="#fff"
              stroke-width="1.6"
              opacity=".55"
            />
          </svg>
        </div>
        <h1 class="brand-title">医生工作站</h1>
        <p class="brand-subtitle">智康云健康管理系统 · 医生端</p>

        <ul class="brand-points">
          <li>
            <span class="dot"></span>
            今日接诊与本周排期一目了然
          </li>
          <li>
            <span class="dot"></span>
            预约列表按日期自动分组
          </li>
          <li>
            <span class="dot"></span>
            与患者端完全隔离的独立工作区
          </li>
        </ul>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="doctor-login__form">
      <div class="form-card">
        <h2 class="form-title">{{ stageTitle }}</h2>
        <p class="form-desc">{{ stageDesc }}</p>

        <!-- 步骤 1：账号密码 -->
        <template v-if="stage === 'login'">
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            size="large"
            @keyup.enter="submit"
          >
            <el-form-item label="医生账号" prop="username">
              <el-input
                v-model.trim="form.username"
                placeholder="请输入医生账号，如 doctor3001"
                clearable
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="登录密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="首次登录请留空"
                show-password
                clearable
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-alert
              v-if="isFirstLogin"
              type="warning"
              :closable="false"
              show-icon
              title="首次登录"
              description="该账号尚未设置密码，请点击下方按钮设置初始密码。"
              class="first-tip"
            />

            <el-button
              type="primary"
              size="large"
              class="submit-btn"
              :loading="loading"
              @click="submit"
            >
              {{ isFirstLogin ? "设置初始密码并登录" : "登 录" }}
            </el-button>
          </el-form>
        </template>

        <!-- 步骤 2：设置初始密码 -->
        <template v-else>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            :title="`账号 ${form.username}`"
            description="首次登录需设置初始密码：至少 8 位，且须同时包含字母和数字。"
            class="first-tip"
          />

          <el-form
            ref="pwdRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-position="top"
            size="large"
            @keyup.enter="submitPassword"
          >
            <el-form-item label="新密码" prop="password">
              <el-input
                v-model="pwdForm.password"
                type="password"
                placeholder="至少 8 位，含字母和数字"
                show-password
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="确认新密码" prop="confirm">
              <el-input
                v-model="pwdForm.confirm"
                type="password"
                placeholder="请再次输入新密码"
                show-password
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <div class="btn-row">
              <el-button size="large" @click="backToLogin">返回</el-button>
              <el-button
                type="primary"
                size="large"
                class="submit-btn"
                :loading="loading"
                @click="submitPassword"
              >
                设置并登录
              </el-button>
            </div>
          </el-form>
        </template>

        <div class="form-footer">
          <router-link to="/login" class="back-link">
            ← 返回用户登录
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { User, Lock } from "@element-plus/icons-vue";
import doctorRequest from "@/utils/doctorRequest.js";
import {
  setDoctorToken,
  setDoctorInfo,
  getRole,
  homeOf,
} from "@/utils/doctorAuth.js";

export default {
  name: "DoctorLogin",
  components: { User, Lock },
  data() {
    // 与后端 DoctorAuthServiceImpl.validatePasswordStrength 保持一致
    const validateStrength = (rule, value, callback) => {
      if (!value) return callback(new Error("请输入新密码"));
      if (value.length < 8) return callback(new Error("密码长度至少 8 位"));
      if (!/[a-zA-Z]/.test(value) || !/\d/.test(value)) {
        return callback(new Error("密码须同时包含字母和数字"));
      }
      callback();
    };
    const validateConfirm = (rule, value, callback) => {
      if (value !== this.pwdForm.password) {
        return callback(new Error("两次输入的密码不一致"));
      }
      callback();
    };

    return {
      stage: "login", // login | setpwd
      isFirstLogin: false,
      loading: false,
      formRef: null,
      pwdRef: null,
      form: { username: "", password: "" },
      pwdForm: { password: "", confirm: "" },
      rules: {
        username: [
          { required: true, message: "请输入医生账号", trigger: "blur" },
        ],
      },
      pwdRules: {
        password: [{ validator: validateStrength, trigger: "blur" }],
        confirm: [{ validator: validateConfirm, trigger: "blur" }],
      },
    };
  },
  computed: {
    stageTitle() {
      return this.stage === "login" ? "医生登录" : "设置初始密码";
    },
    stageDesc() {
      return this.stage === "login"
        ? "请使用管理员分配给你的医生账号登录"
        : "为首次使用的账号设置一个安全的初始密码";
    },
  },
  methods: {
    backToLogin() {
      this.stage = "login";
      this.isFirstLogin = false;
      this.pwdForm = { password: "", confirm: "" };
    },

    /** 步骤 1：提交账号密码。首次登录时后端会返回 needInitPassword=true 而不发 token */
    async submit() {
      if (!this.form.username) {
        this.$message.warning("请输入医生账号");
        return;
      }
      this.loading = true;
      try {
        const { data } = await doctorRequest.post("/doctor/login", {
          username: this.form.username,
          password: this.form.password || null,
        });

        if (data.code !== 200) {
          this.$message.error(data.msg || "登录失败");
          return;
        }

        const payload = data.data || {};
        if (payload.needInitPassword) {
          // 后端已确认是首次登录，切换到设密步骤
          this.isFirstLogin = true;
          this.stage = "setpwd";
          this.$message.info("首次登录，请设置初始密码");
          return;
        }
        this.onLoginSuccess(payload);
      } catch (e) {
        // 业务错误已在 doctorRequest 拦截器提示，这里只兜底网络异常
        if (!e.response) {
          this.$message.error("无法连接服务器，请确认后端已启动");
        }
      } finally {
        this.loading = false;
      }
    },

    /** 步骤 2：设置初始密码并完成登录 */
    async submitPassword() {
      if (!this.pwdRef) return;
      try {
        await this.pwdRef.validate();
      } catch (e) {
        return; // 校验失败，el-form 已自动提示
      }

      this.loading = true;
      try {
        const { data } = await doctorRequest.post("/doctor/login", {
          username: this.form.username,
          password: this.form.password || null,
          newPassword: this.pwdForm.password,
        });

        if (data.code !== 200) {
          this.$message.error(data.msg || "设置密码失败");
          return;
        }
        this.onLoginSuccess(data.data || {});
      } catch (e) {
        if (!e.response) {
          this.$message.error("无法连接服务器，请确认后端已启动");
        }
      } finally {
        this.loading = false;
      }
    },

    onLoginSuccess(payload) {
      if (!payload.token) {
        this.$message.error("后端未返回 token，请稍后重试");
        return;
      }
      setDoctorToken(payload.token);
      setDoctorInfo({
        doctorId: payload.doctorId,
        name: payload.name,
        home: payload.home,
      });
      this.$message.success("登录成功");
      // 双保险：后端 home 字段与前端本地判断取交集，防止被篡改
      const role = getRole("doctor");
      this.$router.push(payload.home || homeOf(role));
    },
  },
};
</script>

<style scoped>
.doctor-login {
  display: flex;
  min-height: 100vh;
  background: #f4f7fb;
}

/* ---------- 左侧品牌 ---------- */
.doctor-login__brand {
  flex: 0 0 46%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(150deg, #1b6ef3 0%, #0d47a1 62%, #08306b 100%);
  position: relative;
  overflow: hidden;
}
.doctor-login__brand::after {
  content: "";
  position: absolute;
  width: 520px;
  height: 520px;
  right: -180px;
  bottom: -200px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
}
.brand-inner {
  position: relative;
  z-index: 1;
  color: #fff;
  padding: 0 40px;
  max-width: 420px;
}
.brand-badge {
  width: 58px;
  height: 58px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 26px;
  backdrop-filter: blur(6px);
}
.brand-title {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 2px;
  margin: 0 0 10px;
}
.brand-subtitle {
  font-size: 14px;
  opacity: 0.78;
  margin: 0 0 40px;
  letter-spacing: 1px;
}
.brand-points {
  list-style: none;
  padding: 0;
  margin: 0;
}
.brand-points li {
  display: flex;
  align-items: center;
  font-size: 14.5px;
  line-height: 2.2;
  opacity: 0.92;
}
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7fc4ff;
  margin-right: 12px;
  flex-shrink: 0;
}

/* ---------- 右侧表单 ---------- */
.doctor-login__form {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}
.form-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 16px;
  padding: 38px 36px 26px;
  box-shadow: 0 8px 32px rgba(20, 45, 90, 0.08);
  border: 1px solid #e8eef7;
}
.form-title {
  font-size: 23px;
  font-weight: 650;
  margin: 0 0 8px;
  color: #1b2433;
}
.form-desc {
  font-size: 13.5px;
  color: #7c8798;
  margin: 0 0 26px;
  line-height: 1.6;
}
.first-tip {
  margin-bottom: 18px;
}
.submit-btn {
  width: 100%;
  margin-top: 6px;
  font-size: 15.5px;
  letter-spacing: 4px;
  font-weight: 600;
  height: 46px;
}
.btn-row {
  display: flex;
  gap: 12px;
}
.btn-row .submit-btn {
  flex: 1;
  margin-top: 0;
}
.form-footer {
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid #eef2f7;
  text-align: center;
}
.back-link {
  font-size: 13px;
  color: #7c8798;
  text-decoration: none;
  transition: color 0.2s;
}
.back-link:hover {
  color: #1b6ef3;
}

@media (max-width: 900px) {
  .doctor-login {
    flex-direction: column;
  }
  .doctor-login__brand {
    flex: none;
    padding: 44px 0;
  }
  .brand-inner {
    text-align: center;
  }
  .brand-badge {
    margin: 0 auto 18px;
  }
  .brand-points {
    display: none;
  }
}
</style>
