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
  padding: 10px 0;
}

.top-news-card {
  padding: 12px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 25px rgba(102, 126, 234, 0.12);
  }
}

.top-news-img {
  width: 100%;
  height: 118px;
  border-radius: 8px;
  object-fit: cover;
}

.top-news-meta {
  font-size: 12px;
  margin-top: 8px;
}

.news-time {
  margin-left: 8px;
  color: #a0a8b8;
}

.news-tags {
  display: inline-block;
  padding: 3px 8px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1), rgba(118, 75, 162, 0.1));
  color: #667eea;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
}

.new-item {
  margin-bottom: 16px;
  padding: 0 8px;
  box-sizing: border-box;
}

.news-card {
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 25px rgba(102, 126, 234, 0.12);
  }

  &:hover .news-card-img {
    transform: scale(1.05);
  }
}

.news-card-img {
  width: 100%;
  height: 120px;
  object-fit: cover;
  transition: transform 0.4s ease;
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
