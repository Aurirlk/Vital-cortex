<template>
  <el-row :gutter="30">
    <el-col :span="18">
      <div class="news-detail-main">
        <div class="news-detail-card">
          <h1 class="news-detail-title">{{ newsInfo.name }}</h1>
          <div class="news-detail-meta">
            <span class="news-tags">{{ newsInfo.tagName }}</span>
            <span class="news-detail-time">{{
              parseTime(newsInfo.createTime)
            }}</span>
            <el-button
              style="margin-left: 20px"
              @click="saveNewsOperation"
              class="customer"
              size="small"
              >{{ !saveFlag ? "立即收藏" : "取消收藏" }}</el-button
            >
          </div>
          <div class="news-detail-content" v-html="sanitizeHtml(newsInfo.content)"></div>
        </div>
        <div class="news-detail-card" style="margin-top: 20px">
          <Evaluations :contentId="newsInfo.id" contentType="NEWS" />
        </div>
      </div>
    </el-col>
    <el-col :span="6">
      <div class="sidebar-card">
        <h3 class="sidebar-title">资讯推荐</h3>
        <el-col
          @click="newsItemClick(news)"
          :span="24"
          :key="index"
          v-for="(news, index) in newsTopList"
        >
          <div class="recommend-item">
            <img :src="news.cover" :alt="news.name" class="recommend-img" />
            <h3 class="news-title">{{ news.name }}</h3>
            <div class="recommend-meta">
              <span class="news-tags">{{ news.tagName }}</span>
              <span class="recommend-time">{{
                parseTime(news.createTime)
              }}</span>
            </div>
          </div>
        </el-col>
      </div>
    </el-col>
  </el-row>
</template>
<script>
import { timeAgo } from "@/utils/data";
import Evaluations from "@/components/Evaluations.vue";
export default {
  components: { Evaluations },
  name: "NewsDetail",
  data() {
    return {
      newsInfo: {},
      newsTopList: [],
      saveFlag: false,
      newsSaveList: [],
    };
  },
  created() {
    this.getStorageInfo();
    this.loadAllTopNews();
  },
  methods: {
    sanitizeHtml(html) {
      if (!html) return "";
      return html
        .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, "")
        .replace(/<iframe\b[^<]*(?:(?!<\/iframe>)<[^<]*)*<\/iframe>/gi, "")
        .replace(/<object\b[^<]*(?:(?!<\/object>)<[^<]*)*<\/object>/gi, "")
        .replace(/<embed\b[^<]*\/?>/gi, "")
        .replace(/\s*on\w+\s*=\s*["'][^"']*["']/gi, "")
        .replace(/javascript:/gi, "");
    },
    loadSaveStatus() {
      if (!this.newsInfo || !this.newsInfo.id) return;
      const newsSaveQueryDto = {
        newsId: this.newsInfo.id,
      };
      this.$axios
        .post("/news-save/queryUser", newsSaveQueryDto)
        .then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.saveFlag = data.data.length !== 0;
          }
        })
        .catch(() => {});
    },
    // 收藏或取消收藏操作
    saveNewsOperation() {
      this.$axios
        .post("/news-save/operation", { newsId: this.newsInfo.id })
        .then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.$message.success(!this.saveFlag ? "收藏成功" : "取消收藏成功");
            this.saveFlag = !this.saveFlag;
          }
        })
        .catch(() => {});
    },
    newsItemClick(news) {
      this.newsInfo = news;
      this.loadSaveStatus();
    },
    parseTime(time) {
      return timeAgo(time);
    },
    getStorageInfo() {
      const newInfo = sessionStorage.getItem("newsInfo");
      if (newInfo) {
        const parsed = JSON.parse(newInfo);
        // 如果没有content字段，填充默认内容以便v-html能渲染
        if (!parsed.content) {
          parsed.content = `
            <p style="font-size:16px;line-height:1.8;color:#4a5568;">
              ${parsed.name || "暂无内容"}
            </p>
            <p style="font-size:14px;line-height:1.8;color:#718096;margin-top:16px;">
              这是一篇关于${
                parsed.tagName || "健康"
              }的文章。更多内容请等待后端API接入后查看。
            </p>
          `;
        }
        this.newsInfo = parsed;
      }
      this.loadSaveStatus();
    },
    loadAllTopNews() {
      const newQueryDto = { isTop: true };
      this.$axios
        .post("/news/query", newQueryDto)
        .then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.newsTopList = data.data;
          }
        })
        .catch(() => {});
    },
  },
};
</script>

<style scoped lang="scss">
.news-detail-main {
  padding-right: 10px;
}

.news-detail-card {
  background: #fff;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.sidebar-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.news-detail-title {
  font-size: 26px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 16px 0;
  line-height: 1.4;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.news-detail-meta {
  font-size: 13px;
  background: #f8fafc;
  padding: 14px 16px;
  border-radius: 10px;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
}

.news-detail-time {
  margin-left: 12px;
  color: #a0aec0;
}

.news-detail-content {
  font-size: 16px;
  line-height: 1.8;
  color: #2d3748;
}

.sidebar-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.sidebar-title {
  padding: 0 0 12px 0;
  font-size: 17px;
  font-weight: 600;
  color: #2d3748;
  border-bottom: 2px solid #f0f2f5;
  margin-bottom: 12px;
}

.recommend-item {
  padding: 16px;
  border-radius: 10px;
  transition: all 0.3s ease;
  cursor: pointer;

  &:hover {
    background: #f8f9ff;
    transform: translateX(3px);
  }
}

.recommend-img {
  width: 100%;
  height: 110px;
  border-radius: 8px;
  object-fit: cover;
}

.recommend-meta {
  font-size: 12px;
  margin-top: 6px;
}

.recommend-time {
  margin-left: 8px;
  color: #a0aec0;
}

.news-tags {
  display: inline-block;
  padding: 3px 10px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1), rgba(118, 75, 162, 0.1));
  color: #667eea;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.news-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 8px 0 4px 0;
  font-size: 13px;
  color: #2d3748;
}
</style>
