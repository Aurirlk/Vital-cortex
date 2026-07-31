<template>
  <div class="register-containel">
    <div class="register-panel">
      <div class="logo">
        <Logo sysName="" />
      </div>
      <div class="text">
        <input v-model="act" class="act" placeholder="" />
      </div>
      <div class="text">
        <input v-model="name" class="act" placeholder="" />
      </div>
      <div class="text">
        <input
          v-model="pwd"
          class="pwd"
          type="password"
          placeholder=""
        />
      </div>
      <div class="text">
        <input
          v-model="pwdConfirm"
          class="pwd"
          type="password"
          placeholder=""
        />
      </div>
      <div>
        <span class="register-btn" @click="registerFunc"></span>
      </div>
      <div class="tip">
        <p><span class="no-act" @click="toDoLogin"></span></p>
      </div>
    </div>
  </div>
</template>

<script>
const DELAY_TIME = 1300;
import request from "@/utils/request.js";
import md5 from "js-md5";
import Logo from "@/components/Logo.vue";
export default {
  name: "Register",
  components: { Logo },
  data() {
    return {
      act: "", // 
      pwd: "", // 
      pwdConfirm: "", // 
      name: "", // 
    };
  },
  methods: {
    // 
    toDoLogin() {
      this.$router.push("/login");
    },
    async registerFunc() {
      if (!this.act || !this.pwd || !this.pwdConfirm || !this.name) {
        this.$swal.fire({
          title: "",
          text: "",
          icon: "error",
          showConfirmButton: false,
          timer: DELAY_TIME,
        });
        return;
      }
      if (this.pwd !== this.pwdConfirm) {
        this.$swal.fire({
          title: "",
          text: "",
          icon: "error",
          showConfirmButton: false,
          timer: DELAY_TIME,
        });
        return;
      }
      const hashedPwd = md5(md5(this.pwd));
      const paramDTO = {
        userAccount: this.act,
        userPwd: hashedPwd,
        userName: this.name,
      };
      try {
        const { data } = await request.post(`user/register`, paramDTO);
        if (data.code !== 200) {
          this.$swal.fire({
            title: "",
            text: data.msg,
            icon: "error",
            showConfirmButton: false,
            timer: DELAY_TIME,
          });
          return;
        }
        // Swal
        this.$swal.fire({
          title: "",
          text: "...",
          icon: "success",
          showConfirmButton: false,
          timer: DELAY_TIME,
        });
        // 
        setTimeout(() => {
          this.$router.push("/login");
        }, DELAY_TIME);
      } catch (error) {
        console.error(":", error);
      }
    },
  },
};
</script>

<style lang="scss" scoped>
* {
  user-select: none;
}

.register-containel {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  position: relative;
  overflow: hidden;

  &::before {
    content: "";
    position: absolute;
    top: -50%;
    left: -50%;
    width: 200%;
    height: 200%;
    background: radial-gradient(
      circle,
      rgba(255, 255, 255, 0.1) 0%,
      transparent 60%
    );
    animation: pulse 8s ease-in-out infinite;
  }

  @keyframes pulse {
    0%,
    100% {
      transform: scale(1);
      opacity: 0.5;
    }
    50% {
      transform: scale(1.1);
      opacity: 0.8;
    }
  }

  .register-panel {
    position: relative;
    z-index: 1;
    margin: 0 auto;
    width: 380px;
    height: auto;
    padding: 50px 40px 30px 40px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(20px);
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15), 0 8px 20px rgba(0, 0, 0, 0.1);

    .logo {
      margin: 10px 0 30px 0;
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
  }

  .register-btn {
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
      font-size: 14px;
      margin: 0;
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
