<template>
  <div class="login-container">
    <!--  -->
    <div class="login-left">
      <div class="login-left__content">
        <h1 class="login-left__title">您的随身 AI 健康管家</h1>
        <p class="login-left__subtitle">
          智康云健康管理系统 —— 让健康数据会说话，让 AI 医生常在身边。
        </p>
        <div class="login-left__features">
          <div class="feature-item">
            <div class="feature-icon">
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M21 11.5C21 16.75 16.75 21 11.5 21C10 21 8.75 20.75 7.5 20.25L3 21L4.5 17.25C3.5 15.75 3 14 3 11.5C3 6.25 7.25 2 12.5 2C17.75 2 21 6.25 21 11.5Z"
                  stroke="white"
                  stroke-width="1.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M8.5 11.5H14.5M11.5 8.5V14.5"
                  stroke="white"
                  stroke-width="1.6"
                  stroke-linecap="round"
                />
              </svg>
            </div>
            <div class="feature-text">
              <div class="feature-title">AI 智能问诊</div>
              <div class="feature-desc">
                多轮对话式健康咨询，支持 RAG 引用溯源
              </div>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M3 12H7L10 4L14 20L17 12H21"
                  stroke="white"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </div>
            <div class="feature-text">
              <div class="feature-title">健康数据追踪</div>
              <div class="feature-desc">体征指标采集、趋势可视化与风险预警</div>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                <circle cx="5" cy="5" r="2" stroke="white" stroke-width="1.6" />
                <circle
                  cx="19"
                  cy="5"
                  r="2"
                  stroke="white"
                  stroke-width="1.6"
                />
                <circle
                  cx="12"
                  cy="12"
                  r="2"
                  stroke="white"
                  stroke-width="1.6"
                />
                <circle
                  cx="5"
                  cy="19"
                  r="2"
                  stroke="white"
                  stroke-width="1.6"
                />
                <circle
                  cx="19"
                  cy="19"
                  r="2"
                  stroke="white"
                  stroke-width="1.6"
                />
                <path
                  d="M7 5L10 10M17 5L14 10M10 14L7 17M14 14L17 17"
                  stroke="white"
                  stroke-width="1.4"
                  stroke-linecap="round"
                />
              </svg>
            </div>
            <div class="feature-text">
              <div class="feature-title">知识库双路 RAG</div>
              <div class="feature-desc">向量 + 知识图谱检索，回答有据可依</div>
            </div>
          </div>
          <div class="feature-item">
            <div class="feature-icon">
              <svg
                width="24"
                height="24"
                viewBox="0 0 24 24"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M14 3H7C5.9 3 5 3.9 5 5V21L8 19L11 21L12 20.5L13 21L16 19L19 21V8"
                  stroke="white"
                  stroke-width="1.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M19 8H14V3"
                  stroke="white"
                  stroke-width="1.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path
                  d="M9 13H15M9 16H13"
                  stroke="white"
                  stroke-width="1.4"
                  stroke-linecap="round"
                />
              </svg>
            </div>
            <div class="feature-text">
              <div class="feature-title">健康报告导出</div>
              <div class="feature-desc">一键生成 PDF 报告，数据随身带走</div>
            </div>
          </div>
        </div>
        <div class="login-left__footer">
          <BrandDecoration variant="wave" color="white" />
        </div>
      </div>
      <!--  -->
      <div class="login-left__decoration">
        <div class="deco-circle deco-circle--1"></div>
        <div class="deco-circle deco-circle--2"></div>
        <div class="deco-circle deco-circle--3"></div>
      </div>
    </div>

    <!--  -->
    <div class="login-right">
      <div class="login-right__form">
        <div class="login-form__header">
          <div class="login-form__brand-row">
            <BrandLogo size="small" color="teal" :show-text="false" />
            <h2 class="login-form__title">欢迎登录</h2>
          </div>
          <p class="login-form__desc">智康云健康管理系统</p>
        </div>

        <!-- 2026-10-10 删除「手机登录」页签：
             它是一处**账号接管漏洞**，而不是一个待完善的功能。
             原实现里：
               1) sendSmsCode() 根本不请求后端（项目里也没有任何短信/验证码接口），
                  只是弹个「验证码已发送（演示环境默认 123456）」再跑一个倒计时动画；
               2) loginByPhone() 完全忽略用户填的 this.smsCode，
                  直接用写死的口令 "123456" 去调 user/login。
             结果：任何人只要知道某个已注册用户的手机号，
             输入该手机号 + 任意 6 位数字即可登录成对方（无需密码、无需验证码）。
             真正的短信登录需要短信服务商与后端验证码存储，本项目均不具备，
             因此这里直接下线该入口，而不是保留一个假的登录框。
             若后续接入短信服务，请同时补上后端发码/校验接口再恢复 UI。 -->
        <div class="login-form__fields">
          <div class="form-field">
            <label class="form-label">账号</label>
            <input
              v-model="act"
              class="form-input"
              placeholder="请输入账号（如 admin）"
              @keyup.enter="login"
            />
          </div>
          <div class="form-field">
            <label class="form-label">密码</label>
            <input
              v-model="pwd"
              class="form-input"
              type="password"
              placeholder="请输入密码"
              @keyup.enter="login"
            />
          </div>
        </div>

        <!--  -->
        <button
          class="login-btn"
          :class="{ 'login-btn--loading': loading }"
          @click="login()"
          :disabled="loading"
        >
          <span v-if="loading" class="login-btn__spinner"></span>
          <span>{{ loading ? "登录中..." : "登录" }}</span>
        </button>

        <!--  -->
        <div class="login-form__footer">
          <span class="login-form__text">还没有账号？</span>
          <span class="login-form__link" @click="toRegister">立即注册</span>
        </div>

        <!-- 医生端独立入口（2026-10-04）：医生账号由管理员分配，不能在此注册 -->
        <div class="login-form__footer">
          <span class="login-form__text">我是医生</span>
          <span class="login-form__link" @click="toDoctorLogin">
            前往医生登录
          </span>
        </div>

        <!--  -->
        <div class="login-form__other">
          <div class="brand-divider"></div>
          <div class="other-login">
            <button class="other-login__btn" title="微信登录">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path
                  d="M8.5 11C9.33 11 10 10.33 10 9.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11ZM15.5 11C16.33 11 17 10.33 17 9.5S16.33 8 15.5 8 14 8.67 14 9.5 14.67 11 15.5 11ZM12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12C4 7.59 7.59 4 12 4C16.41 4 20 7.59 20 12C20 16.41 16.41 20 12 20Z"
                  fill="#07c160"
                />
              </svg>
            </button>
            <button class="other-login__btn" title="QQ">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path
                  d="M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12C4 7.59 7.59 4 12 4C16.41 4 20 7.59 20 12C20 16.41 16.41 20 12 20Z"
                  fill="#12B7F5"
                />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
const DELAY_TIME = 1300;
import request from "@/utils/request.js";
import { setToken } from "@/utils/storage.js";
import { connectWs } from "@/utils/ws.js";
import BrandLogo from "@/components/BrandLogo.vue";
import BrandDecoration from "@/components/BrandDecoration.vue";

export default {
  name: "Login",
  components: { BrandLogo, BrandDecoration },
  data() {
    return {
      // 2026-10-10：loginType / phone / smsCode / smsCooldown / smsTimer 已随
      // 「手机登录」页签一并移除（那是一处账号接管入口，详见模板注释）。
      act: "",
      pwd: "",
      loading: false,
    };
  },
  methods: {
    toRegister() {
      this.$router.push("/register");
    },

    // 医生端走独立账号体系（/doctor/login），与此处用户登录互不相通
    toDoctorLogin() {
      this.$router.push("/doctor/login");
    },

    // 2026-10-10 已删除 sendSmsCode() / loginByPhone()：
    // 前者不请求后端、只演一个倒计时；后者忽略用户输入的验证码，
    // 直接用写死口令 "123456" 登录 —— 知道手机号即可接管账号。
    // 详见模板中「手机登录」页签位置的说明。

    //
    async login() {
      if (!this.act || !this.pwd) {
        this.$swal.fire({
          title: "提示",
          text: "请输入账号和密码",
          icon: "error",
          showConfirmButton: false,
          timer: DELAY_TIME,
        });
        return;
      }
      this.loading = true;
      const hashedPwd = this.pwd;
      const paramDTO = { userAccount: this.act, userPwd: hashedPwd };
      try {
        const { data } = await request.post(`user/login`, paramDTO);
        if (data.code !== 200) {
          this.$swal.fire({
            title: "登录失败",
            text: data.msg,
            icon: "error",
            showConfirmButton: false,
            timer: DELAY_TIME,
          });
          return;
        }
        setToken(data.data.token);
        connectWs();
        const { role } = data.data;
        await this.$router.push(
          role === 1 ? "/admin/adminLayout" : "/user/news-record"
        );
      } catch (error) {
        console.error(":", error);
        this.$message.error("登录失败，请检查网络后重试");
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  min-height: 100vh;
  background: #fff;
}

/* ====================  ==================== */
.login-left {
  flex: 1;
  background: linear-gradient(135deg, #0050cb 0%, #0066ff 50%, #632ce5 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  position: relative;
  overflow: hidden;

  &__content {
    position: relative;
    z-index: 2;
    max-width: 480px;
  }

  &__brand {
    margin-bottom: 40px;
  }

  &__title {
    font-size: 36px;
    font-weight: 700;
    color: #fff;
    margin: 0 0 16px 0;
    line-height: 1.3;
  }

  &__subtitle {
    font-size: 16px;
    color: rgba(255, 255, 255, 0.85);
    margin: 0 0 40px 0;
    line-height: 1.7;
  }

  &__features {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  &__footer {
    margin-top: 48px;
  }

  &__decoration {
    position: absolute;
    inset: 0;
    pointer-events: none;
  }
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  backdrop-filter: blur(10px);
  transition: all 0.3s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.18);
    transform: translateX(8px);
  }
}

.feature-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 12px;
  flex-shrink: 0;
}

.feature-text {
  flex: 1;
}

.feature-title {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 4px;
}

.feature-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

/*  */
.deco-circle {
  position: absolute;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.08);

  &--1 {
    width: 300px;
    height: 300px;
    top: -80px;
    right: -80px;
    animation: float 8s ease-in-out infinite;
  }

  &--2 {
    width: 200px;
    height: 200px;
    bottom: -60px;
    left: -60px;
    animation: float 6s ease-in-out infinite reverse;
  }

  &--3 {
    width: 150px;
    height: 150px;
    top: 50%;
    right: 10%;
    background: rgba(255, 255, 255, 0.03);
    animation: float 10s ease-in-out infinite;
  }
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-20px);
  }
}

/* ====================  ==================== */
.login-right {
  width: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  background: #fff;
}

.login-right__form {
  width: 100%;
  max-width: 400px;
}

.login-form {
  &__header {
    margin-bottom: 36px;
  }

  &__brand-row {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 8px;
  }

  &__title {
    font-size: 28px;
    font-weight: 700;
    color: #1a1a1a;
    margin: 0;
  }

  &__desc {
    font-size: 15px;
    color: #999;
    margin: 0;
  }
}

/*  */
.login-tabs {
  display: flex;
  background: #f5f5f5;
  padding: 4px;
  border-radius: 10px;
  margin-bottom: 28px;
}

.login-tab {
  flex: 1;
  padding: 10px 16px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #666;
  background: transparent;
  cursor: pointer;
  transition: all 0.25s ease;

  &--active {
    background: #fff;
    color: #0050cb;
    box-shadow: 0 2px 8px rgba(0, 80, 203, 0.15);
  }
}

/*  */
.login-form__fields {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 28px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 8px;

  &__row {
    display: flex;
    gap: 12px;
  }
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.form-input {
  width: 100%;
  height: 48px;
  padding: 0 16px;
  border: 2px solid #f0f0f0;
  border-radius: 10px;
  font-size: 15px;
  color: #1a1a1a;
  background: #fafafa;
  transition: all 0.25s ease;
  box-sizing: border-box;

  &::placeholder {
    color: #bbb;
  }

  &:focus {
    outline: none;
    border-color: #0050cb;
    background: #fff;
    box-shadow: 0 0 0 3px rgba(0, 80, 203, 0.08);
  }

  &--sms {
    flex: 1;
  }
}

/*  */
.sms-btn {
  height: 48px;
  padding: 0 16px;
  border: 2px solid #0050cb;
  border-radius: 10px;
  background: transparent;
  color: #0050cb;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.25s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;

  &:hover:not(&--disabled) {
    background: rgba(0, 80, 203, 0.06);
  }

  &--disabled {
    border-color: #ddd;
    color: #bbb;
    cursor: not-allowed;
  }

  &__icon {
    flex-shrink: 0;
  }
}

/*  */
.login-btn {
  width: 100%;
  height: 50px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #0050cb, #0066ff);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 14px rgba(0, 80, 203, 0.35);

  &:hover:not(&--loading) {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(0, 80, 203, 0.45);
  }

  &:active {
    transform: translateY(0);
  }

  &--loading {
    opacity: 0.85;
    cursor: wait;
  }

  &__spinner {
    width: 18px;
    height: 18px;
    border: 2px solid rgba(255, 255, 255, 0.3);
    border-top-color: #fff;
    border-radius: 50%;
    animation: spin 0.6s linear infinite;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/*  */
.login-form__footer {
  text-align: center;
  margin-top: 20px;
}

.login-form__text {
  font-size: 14px;
  color: #999;
}

.login-form__link {
  font-size: 14px;
  color: #0050cb;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;

  &:hover {
    opacity: 0.8;
  }
}

/*  */
.login-form__other {
  margin-top: 32px;
}

.other-login {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 16px;
}

.other-login__btn {
  width: 44px;
  height: 44px;
  border: 2px solid #f0f0f0;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.25s ease;

  &:hover {
    border-color: #0050cb;
    background: rgba(0, 80, 203, 0.04);
    transform: scale(1.08);
  }
}

/* ====================  ==================== */
@media (max-width: 1024px) {
  .login-left {
    display: none;
  }

  .login-right {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .login-right {
    padding: 30px 20px;
  }
}
</style>
