<template>
  <div class="news-save-container">
    <div v-if="newsSaveList.length === 0" class="empty-container">
      <el-empty description="暂未收藏资讯"></el-empty>
    </div>
    <div v-else class="news-save-grid">
      <div
        class="news-save-card"
        v-for="(news, index) in newsSaveList"
        :key="index"
        @click="newsItemClick(news)"
      >
        <img
          :src="news.cover"
          :alt="news.name"
          class="news-cover"
        />
        <div class="news-info">
          <h3 class="news-title">{{ news.name }}</h3>
          <div class="news-meta">
            <span class="news-tags">{{ news.tagName }}</span>
            <span class="news-time">收藏于 {{ parseTime(news.createTime) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import { timeAgo } from "@/utils/data";
export default {
  data() {
    return {
      newsSaveList: [],
    };
  },
  created() {
    this.loadAllSaveNews();
  },
  methods: {
    parseTime(time) {
      return timeAgo(time);
    },
    newsItemClick(newsSave) {
      // 组装资讯
      const news = {
        id: newsSave.newsId,
        name: newsSave.name,
        content: newsSave.content,
        createTime: newsSave.createTime,
        tagName: newsSave.tagName,
      };
      sessionStorage.setItem("newsInfo", JSON.stringify(news));
      this.$router.push("/user/news-detail");
    },
    loadAllSaveNews() {
      // 查询条件，带上ID
      const userInfo = sessionStorage.getItem("userInfo");
      const userInfoEntity = JSON.parse(userInfo);
      const newsSaveQueryDto = {
        userId: userInfoEntity.id,
      };
      this.$axios
        .post("/news-save/queryUser", newsSaveQueryDto)
        .then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.newsSaveList = data.data;
          }
        });
    },
  },
};
</script>
<style scoped lang="scss">
.news-save-container {
  padding: 20px;
}

.empty-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.news-save-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px;
}

.news-save-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(102, 126, 234, 0.15);
  }
}

.news-cover {
  width: 100%;
  height: 140px;
  object-fit: cover;
}

.news-info {
  padding: 12px;
}

.news-title {
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
  margin: 0 0 8px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.news-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #718096;
}

.news-tags {
  display: inline-block;
  padding: 2px 8px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1), rgba(118, 75, 162, 0.1));
  color: #667eea;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.news-time {
  color: #a0aec0;
}
</style>
