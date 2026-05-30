<template>
  <div>
    <div style="line-height: 70px; padding: 0 50px">
      <el-row>
        <el-col :span="6">
          <Logo sysName="健康资讯" />
        </el-col>
        <el-col :span="18">
          <span
            style="
              float: right;
              display: flex;
              align-items: center;
              flex-wrap: wrap;
            "
          >
            <img
              style="width: 30px; height: 30px; border-radius: 15px"
              :src="userInfo.userAvatar"
            />
            <span style="margin-left: 8px">{{ userInfo.userName }}</span>
          </span>
        </el-col>
      </el-row>
    </div>
    <div style="height: 20px; background-color: rgb(248, 248, 248)"></div>
    <div style="padding: 10px 50px">
      <div>
        <p style="font-size: 16px; padding: 10px 0">
          消息中心
          <span @click="clearMessage" class="clear-message">
            <el-icon><Open /></el-icon>
          </span>
        </p>
      </div>
      <div>
        <span
          @click="messageTypeSelected(messageType.type)"
          class="message-type"
          v-for="(messageType, index) in messageTypes"
          :key="index"
        >
          {{ messageType.detail }}
        </span>
      </div>
      <div style="padding: 30px 0">
        <div
          style="margin-bottom: 5px; padding: 5px; border-radius: 5px"
          :style="{
            backgroundColor: !message.isRead ? 'rgb(248,248,248)' : '',
          }"
          v-for="(message, index) in messageList"
          :key="index"
        >
          <el-row style="padding: 15px 0">
            <el-col :span="1">
              <span class="bell-icon">
                <el-icon v-if="message.messageType === 1"
                  ><ChatLineRound
                /></el-icon>
                <el-icon v-else-if="message.messageType === 2"
                  ><Promotion
                /></el-icon>
                <el-icon v-else-if="message.messageType === 3"
                  ><DataAnalysis
                /></el-icon>
                <el-icon v-else><Message /></el-icon>
              </span>
            </el-col>
            <el-col :span="23">
              <div>
                <div>
                  <span class="message-content" style="font-size: 14px">{{
                    message.content
                  }}</span>
                </div>
              </div>
              <div>
                <span class="message-time">{{ message.createTime }}</span>
              </div>
            </el-col>
          </el-row>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import Logo from "@/components/Logo";
export default {
  components: { Logo },
  data() {
    return {
      userInfo: {},
      messageQueryDto: {},
      messageList: [],
      messageTypes: [],
      dialogEvaluationsOperation: false,
      message: {},
    };
  },
  created() {
    // 1. 拿到当前用户的信息
    this.getUserInfo();
    // 2. 加载用户全部消息
    this.loadAllUsersMessage();
    // 3. 加载全部的消息类型
    this.loadAllMessageType();
  },
  methods: {
    // 将全部的消息设置未已读
    async clearMessage() {
      const confirmed = await this.$swalConfirm({
        title: "消息清除",
        text: `是否将全部的消息设置为已读？`,
        icon: "warning",
      });
      if (confirmed) {
        this.$axios.put("/message/clearMessage").then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.loadAllUsersMessage();
          }
        });
      }
    },
    evaluationsPut() {
      this.$axios.put("/message/clearMessage").then((response) => {
        const { data } = response;
        if (data.code === 200) {
          this.loadAllUsersMessage();
        }
      });
    },
    // 做回复的
    replyEvalustions(message) {
      this.message = message;
      this.dialogEvaluationsOperation = true;
    },
    // 消息类型选中
    messageTypeSelected(messageType) {
      this.messageQueryDto.messageType = messageType;
      this.loadAllUsersMessage();
    },
    getUserInfo() {
      const userInfo = sessionStorage.getItem("userInfo");
      this.userInfo = JSON.parse(userInfo);
    },
    // 加载全部的消息类型
    loadAllMessageType() {
      this.$axios.get("/message/types").then((response) => {
        const { data } = response;
        if (data.code === 200) {
          this.messageTypes = data.data;
          const messageType = { type: null, detail: "全部消息" };
          this.messageTypes.unshift(messageType);
          this.messageTypes.map((entity) => (entity.isCheck = false));
        }
      });
    },
    loadAllUsersMessage() {
      const userInfo = sessionStorage.getItem("userInfo");
      const entity = JSON.parse(userInfo);
      this.messageQueryDto.userId = entity.id;
      this.$axios
        .post("/message/query", this.messageQueryDto)
        .then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.messageList = data.data;
          }
        });
    },
  },
};
</script>
<style scoped lang="scss">
.bell-icon {
  display: inline-block;
  height: 30px;
  width: 30px;
  border-radius: 20px;
  background-color: rgb(82, 152, 237);
  border: 3px solid rgb(212, 227, 230);

  i {
    line-height: 30px;
    width: 30px;
    text-align: center;
    font-size: 25px;
    color: #f1f1f1;
  }
}

.message-time {
  font-size: 12px;
  color: rgb(131, 104, 102);
}

.clear-message {
  display: inline-block;
  margin-left: 10px;
  padding: 6px 12px;
  border-radius: 5px;
}

.clear-message:hover {
  background-color: #f1f1f1;
}

.news-tags {
  display: inline-block;
  padding: 2px 5px;
  background-color: rgb(222, 243, 251);
  color: #1d3cc4;
  font-size: 14px;
  border-radius: 3px;
}

.message-type {
  display: inline-block;
  font-size: 18px;
  margin-right: 25px;
  cursor: pointer;
}

.message-content {
  display: inline-block;
  margin: 5px 0;
  font-size: 22px;
}
</style>
