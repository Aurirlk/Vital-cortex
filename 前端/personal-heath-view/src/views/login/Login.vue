<template>
    <div class="login-container" :style="{ backgroundImage: 'url(/bag.png)' }">
        <div class="login-panel">
      <div class="logo">
        <Logo :bag="colorLogo" sysName="健康资讯" />
      </div>
      <div class="text">
        <input v-model="act" class="act" placeholder="账号" />
      </div>
      <div class="text">
        <input v-model="pwd" class="pwd" type="password" placeholder="密码" />
      </div>
      <div>
        <span class="login-btn" @click="login">立即登录</span>
      </div>
      <div class="tip">
        <p>
          没有账号？<span class="no-act" @click="toDoRegister">点此注册</span>
        </p>
      </div>
    </div>
  </div>
</template>

<script>
const ADMIN_ROLE = 1;
const USER_ROLE = 2;
const DELAY_TIME = 1300;
import request from "@/utils/request.js";
import { setToken } from "@/utils/storage.js";
import md5 from "js-md5";
import Logo from "@/components/Logo.vue";
export default {
  name: "Login",
  components: { Logo },
  data() {
    return {
      act: "",
      pwd: "",
      colorLogo: "rgb(38,38,38)",
    };
  },
  methods: {
    // 跳转注册页面
    toDoRegister() {
      this.$router.push("/register");
    },
    async login() {
      if (!this.act || !this.pwd) {
        this.$swal.fire({
          title: "填写校验",
          text: "账号或密码不能为空",
          icon: "error",
          showConfirmButton: false,
          timer: DELAY_TIME,
        });
        return;
      }
      const hashedPwd = md5(md5(this.pwd));
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
        const { role } = data.data;
        console.log("[Login] token set, role=", role, "navigating...");
        await this.$router.push(
          role === ADMIN_ROLE ? "/admin/adminLayout" : "/user/news-record"
        );
        console.log(
          "[Login] navigation complete, current path:",
          this.$route.path
        );
      } catch (error) {
        console.error("登录请求错误:", error);
        this.$message.error("登录请求出错，请重试！");
      }
    },
    navigateToRole(role) {
      switch (role) {
        case ADMIN_ROLE:
          this.$router.push("/admin/adminLayout");
          break;
        case USER_ROLE:
          this.$router.push("/user/news-record");
          break;
        default:
          console.warn("未知的角色类型:", role);
          break;
      }
    },
  },
};
</script>

<style lang="scss" scoped>
* {
  user-select: none;
}
.login-container {
  width: 100%;
  min-height: 100vh;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-color: #667eea;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  position: relative;
  overflow: hidden;

  &::before {
    content: "";
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, rgba(102, 126, 234, 0.4), rgba(118, 75, 162, 0.4));
    z-index: 0;
  }

  .login-panel {
    position: relative;
    z-index: 1;
    width: 380px;
    height: auto;
    padding: 50px 40px 30px 40px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(20px);
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15), 0 8px 20px rgba(0, 0, 0, 0.1);

    .logo {
      margin: 10px 0 35px 0;
      text-align: center;
    }

    .act,
    .pwd {
      margin: 8px 0;
      height: 50px;
      line-height: 50px;
      width: 100%;
      background-color: #f5f7fa;
      box-sizing: border-box;
      border: 2px solid transparent;
      border-radius: 10px;
      font-weight: 600;
      font-size: 16px;
      padding: 0 18px;
      margin-top: 15px;
      transition: all 0.3s ease;
    }

    .act:focus,
    .pwd:focus {
      outline: none;
      background-color: #fff;
      border-color: #667eea;
      box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.15);
    }

    .act::placeholder,
    .pwd::placeholder {
      color: #aab;
      font-weight: 400;
    }

    .role {
      display: inline-block;
      color: rgb(30, 102, 147);
      font-size: 14px;
      padding-right: 10px;
    }
  }

  .login-btn {
    display: inline-block;
    text-align: center;
    border-radius: 10px;
    margin-top: 25px;
    height: 48px;
    line-height: 48px;
    width: 100%;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    font-size: 16px !important;
    font-weight: 600;
    border: none;
    color: #fff;
    padding: 0 !important;
    cursor: pointer;
    user-select: none;
    transition: all 0.3s ease;
    box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
    }

    &:active {
      transform: translateY(0);
    }
  }

  .tip {
    margin: 25px 0 10px 0;
    text-align: center;

    p {
      padding: 3px 0;
      margin: 0;
      font-size: 14px;
      color: #8e99a9;

      i {
        margin-right: 3px;
      }

      span {
        color: #3b3c3e;
        border-radius: 2px;
        margin: 0 6px;
      }

      .no-act {
        color: #667eea;
        font-weight: 500;
        transition: color 0.3s;

        &:hover {
          color: #764ba2;
          cursor: pointer;
        }
      }
    }
  }
}
</style>
