<template>
  <div class="home-container">
    <el-row :gutter="24">
      <el-col :span="7">
        <Banner :data="newsTopList" @on-click="onBannerClick" />
      </el-col>
      <el-col :span="17">
        <el-row :gutter="16">
          <el-col
            @click="newsItemClick(news)"
            :span="6"
            :key="index"
            v-for="(news, index) in newsTopList"
          >
            <div class="top-news-card">
              <img :src="news.cover" :alt="news.name" class="top-news-img" />
              <h3 class="news-title">{{ news.name }}</h3>
              <div class="top-news-meta">
                <span class="news-tags">{{ news.tagName }}</span>
                <span class="news-time">{{ parseTime(news.createTime) }}</span>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-col>
    </el-row>
    <el-row style="margin-top: 20px">
      <TagLine :dataList="tagsList" @on-click="tagOnClick" />
    </el-row>
    <el-row :gutter="16">
      <el-row v-if="newsList.length === 0" style="width: 100%">
        <el-empty description="暂无资讯"></el-empty>
      </el-row>
      <el-col
        class="new-item"
        @click="newsItemClick(news)"
        :span="4"
        :key="index"
        v-for="(news, index) in newsList"
      >
        <div class="news-card">
          <img :src="news.cover" :alt="news.name" class="news-card-img" />
          <div class="news-card-body">
            <h3 class="news-title">{{ news.name }}</h3>
            <div class="news-card-meta">
              <span class="news-tags">{{ news.tagName }}</span>
              <span class="news-time">{{ parseTime(news.createTime) }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>
<script>
import TagLine from "@/components/TagLine";
import Banner from "@/components/Banner";
import { timeAgo } from "@/utils/data";
export default {
  components: { TagLine, Banner },
  data() {
    return {
      tagsList: [],
      newsList: [],
      newsTopList: [],
      newQueryDto: { tagId: null },
    };
  },
  created() {
    this.loadAllTags();
    this.loadAllNews();
    this.loadAllTopNews();
  },
  methods: {
    // 轮播图点击事件回传
    onBannerClick(banner) {
      sessionStorage.setItem("newsInfo", JSON.stringify(banner));
      this.$router.push("/user/news-detail");
    },
    // 健康资讯列表的项点击事件
    newsItemClick(news) {
      sessionStorage.setItem("newsInfo", JSON.stringify(news));
      this.$router.push("/user/news-detail");
    },
    // 转换时间
    parseTime(time) {
      return timeAgo(time);
    },
    tagOnClick(tags) {
      this.newQueryDto.tagId = tags.id;
      this.loadAllNews();
    },
    loadAllTags() {
      this.$axios.post('/tags/query', {}).then(response => {
        const { data } = response;
        if (data.code === 200) {
          this.tagsList = data.data;
          this.tagsList.unshift({ name: '全部', id: null });
        }
      }).catch(() => {});
    },
    loadAllTopNews() {
      const newQueryDto = { isTop: true };
      this.$axios.post('/news/query', newQueryDto).then(response => {
        const { data } = response;
        if (data.code === 200) {
          this.newsTopList = data.data;
        }
      }).catch(() => {});
    },
    loadAllNews() {
      this.$axios.post('/news/query', this.newQueryDto).then(response => {
        const { data } = response;
        if (data.code === 200) {
          this.newsList = data.data;
        }
      }).catch(() => {});
    },
  },
};
</script>

<style scoped lang="scss">
.home-container {
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  min-height: 100vh;
}

.top-news-card {
  padding: 16px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  cursor: pointer;
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 4px;
    background: linear-gradient(90deg, #667eea, #764ba2);
    transform: scaleX(0);
    transition: transform 0.4s ease;
  }

  &:hover {
    transform: translateY(-8px) scale(1.02);
    box-shadow: 0 12px 40px rgba(102, 126, 234, 0.2);

    &::before {
      transform: scaleX(1);
    }
  }
}

.top-news-img {
  width: 100%;
  height: 130px;
  border-radius: 12px;
  object-fit: cover;
  transition: transform 0.4s ease;

  &:hover {
    transform: scale(1.05);
  }
}

.top-news-meta {
  font-size: 12px;
  margin-top: 12px;
}

.news-time {
  margin-left: 8px;
  color: #a0a8b8;
}

.news-tags {
  display: inline-block;
  padding: 4px 12px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.15), rgba(118, 75, 162, 0.15));
  color: #667eea;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.new-item {
  margin-bottom: 20px;
  padding: 0 8px;
  box-sizing: border-box;
}

.news-card {
  border-radius: 16px;
  background: #fff;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  cursor: pointer;
  position: relative;

  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: linear-gradient(90deg, #667eea, #764ba2);
    transform: scaleX(0);
    transition: transform 0.4s ease;
  }

  &:hover {
    transform: translateY(-8px) scale(1.02);
    box-shadow: 0 12px 40px rgba(102, 126, 234, 0.2);

    &::after {
      transform: scaleX(1);
    }
  }

  &:hover .news-card-img {
    transform: scale(1.08);
  }

  &:hover .news-card-body {
    background: linear-gradient(135deg, #f8f9ff 0%, #fff 100%);
  }
}

.news-card-img {
  width: 100%;
  height: 140px;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.news-card-body {
  padding: 16px;
  transition: background 0.3s ease;
}

.news-title {
  font-size: 14px;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 0 8px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.news-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.news-card-body {
  padding: 12px;
}

.news-card-meta {
  font-size: 12px;
  margin-top: 8px;
}

.news-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 8px 0 4px 0;
  font-size: 14px;
  color: #2d3748;
  line-height: 1.4;
}
</style>
