<template>
  <div class="home-container">
    <!--
      官网结构：章节锚点条 → 首屏 → 服务入口 → 资讯头条 → 栏目导航 → 最新资讯 → 页脚。
      这里**刻意不做 position: sticky** —— 外层 .content-container 设了 overflow-x: hidden，
      而 overflow-x: hidden + overflow-y: visible 计算后是 overflow-y: auto，
      于是它成了最近的滚动祖先（尽管它实际上不滚动）。sticky 会相对它定位，
      结果是锚点条跟着页面一起滚走，看起来像没生效。要真做吸顶得先动外层容器，
      那会牵到别的页面，不值当。
    -->
    <nav class="site-nav" aria-label="页面章节">
      <span class="site-nav__brand">智康云</span>
      <ul class="site-nav__list">
        <li v-for="item in navItems" :key="item.key">
          <button
            type="button"
            class="site-nav__link"
            @click="scrollTo(item.key)"
          >
            {{ item.label }}
          </button>
        </li>
      </ul>
    </nav>

    <!-- ============ 首屏 ============ -->
    <section ref="top" class="hero">
      <div class="hero__body">
        <p class="hero__eyebrow">智康云 · 个人健康管理系统</p>
        <h1 class="hero__title">
          把日常健康管理，<br />
          交给一个更懂你的系统
        </h1>
        <p class="hero__desc">
          健康资讯、AI 问诊、预约挂号、健康测评与随访记录都在一处。
          知识网络把科室、症状与知识库条目连成一张图，让每一次提问都能落到有依据的答案上。
        </p>
        <div class="hero__actions">
          <button type="button" class="btn btn--primary" @click="goFeature('/user/ai-analysis')">
            <el-icon><FirstAidKit /></el-icon>
            体验 AI 问诊
          </button>
          <button type="button" class="btn btn--ghost" @click="scrollTo('serve')">
            查看全部服务
            <el-icon><ArrowDown /></el-icon>
          </button>
        </div>
        <p class="hero__note">
          分析结论仅作健康参考，不替代执业医师的诊断与处方。
        </p>
      </div>

      <div class="hero__visual">
        <!--
          labels 取真实栏目名（栏目接口没返回时组件用内置健康主题词表兜底）。
          传真实数据而不是硬编码词表，是为了让这张图真的反映站内有什么内容。
        -->
        <NetworkCanvas :labels="heroLabels" height="340px" />
        <p class="hero__visual-caption">知识网络 · 科室 / 症状 / 知识库条目关联</p>
      </div>
    </section>

    <!-- ============ 服务入口 ============ -->
    <section ref="serve" class="section">
      <header class="section-head" v-reveal>
        <h2 class="section-head__title">服务入口</h2>
        <p class="section-head__desc">常用功能集中在这里，点开即用</p>
      </header>
      <div class="feature-grid">
        <button
          v-for="(item, index) in features"
          :key="item.path"
          type="button"
          class="feature-card"
          v-reveal="{ delay: index * 70 }"
          @click="goFeature(item.path)"
        >
          <span class="feature-card__icon">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <span class="feature-card__title">{{ item.title }}</span>
          <span class="feature-card__desc">{{ item.desc }}</span>
          <span class="feature-card__more">
            进入
            <el-icon><ArrowRight /></el-icon>
          </span>
        </button>
      </div>
    </section>

    <!-- ============ 资讯头条（原结构，保持不变） ============ -->
    <section ref="headline" class="section">
      <header class="section-head" v-reveal>
        <h2 class="section-head__title">资讯头条</h2>
        <p class="section-head__desc">轮播与置顶资讯</p>
      </header>
      <el-row :gutter="24" v-reveal>
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
                <div
                  class="top-news-img cover-gradient"
                  :style="coverStyle(news)"
                ></div>
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
    </section>

    <!-- ============ 栏目导航（原结构，保持不变） ============ -->
    <section ref="channel" class="section" v-reveal>
      <el-row>
        <TagLine :dataList="tagsList" @on-click="tagOnClick" />
      </el-row>
    </section>

    <!-- ============ 最新资讯（原结构，保持不变） ============ -->
    <section ref="latest" class="section">
      <header class="section-head" v-reveal>
        <h2 class="section-head__title">最新资讯</h2>
        <p class="section-head__desc">
          {{ activeTagName ? `当前栏目：${activeTagName}` : "全部栏目" }}
        </p>
      </header>
      <el-row :gutter="16">
        <el-row v-if="newsList.length === 0" style="width: 100%">
          <el-empty description=""></el-empty>
        </el-row>
        <el-col
          class="new-item"
          @click="newsItemClick(news)"
          :span="4"
          :key="index"
          v-for="(news, index) in newsList"
        >
          <div class="news-card">
            <div
              class="news-card-img cover-gradient"
              :style="coverStyle(news)"
            ></div>
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
    </section>

    <!-- ============ 页脚 ============ -->
    <footer class="site-footer">
      <div class="site-footer__main">
        <div class="site-footer__brand">
          <span class="site-footer__logo">智康云</span>
          <p class="site-footer__slogan">
            个人健康管理系统 · 资讯 / 问诊 / 预约 / 测评 / 随访
          </p>
        </div>
        <ul class="site-footer__links">
          <li
            v-for="item in footerLinks"
            :key="item.path"
            @click="goFeature(item.path)"
          >
            {{ item.title }}
          </li>
        </ul>
        <div class="site-footer__meta">
          <p>
            本平台提供的分析与建议仅作健康参考，<br />
            不能替代执业医师的诊断与处方。
          </p>
          <button type="button" class="site-footer__top" @click="scrollTo('top')">
            回到顶部
          </button>
        </div>
      </div>
      <div class="site-footer__bar">
        <span>© {{ year }} 智康云健康管理系统</span>
        <span>数据仅用于个人健康管理</span>
      </div>
    </footer>
  </div>
</template>
<script>
import TagLine from "@/components/TagLine";
import Banner from "@/components/Banner";
import NetworkCanvas from "@/components/effects/NetworkCanvas.vue";
import { timeAgo } from "@/utils/data";
export default {
  // 显式命名。原文件没有 name，ESLint 用文件名推断出 "Home" 并报
  // vue/multi-word-component-names（单词名在 DevTools 里会和其它 Home 撞车）。
  // 从路由 path 取 UserHome，语义清楚且不与 admin/Home.vue 混淆。
  name: "UserHome",
  components: { TagLine, Banner, NetworkCanvas },
  data() {
    return {
      tagsList: [],
      newsList: [],
      newsTopList: [],
      newQueryDto: { tagId: null },
      // 章节锚点。key 对应模板里的 ref，label 是导航文案。
      navItems: [
        { key: "top", label: "首页" },
        { key: "serve", label: "服务" },
        { key: "headline", label: "头条" },
        { key: "channel", label: "栏目" },
        { key: "latest", label: "最新资讯" },
      ],
      // 服务入口。icon 用 main.js 里已全局注册的 Element Plus 图标，
      // 所以模板里直接 <component :is="item.icon" /> 就能渲染。
      features: [
        {
          icon: "FirstAidKit",
          title: "AI 问诊",
          desc: "描述症状，获得分诊方向与用药参考，支持上传检查图片",
          path: "/user/ai-analysis",
        },
        {
          icon: "ChatDotRound",
          title: "健康助手",
          desc: "多轮对话随时追问，回答会带上知识库来源",
          path: "/user/assistant",
        },
        {
          icon: "Calendar",
          title: "预约挂号",
          desc: "按日期与时段挑选医生，可先标记自己有空的时间段",
          path: "/user/appointment",
        },
        {
          icon: "Tickets",
          title: "健康测评",
          desc: "量表式自评，生成可追踪、可对比的评估结果",
          path: "/user/quiz",
        },
        {
          icon: "ShoppingCart",
          title: "健康商城",
          desc: "按价格区间筛选健康商品，边看边比价",
          path: "/user/mall",
        },
        {
          icon: "DataLine",
          title: "健康报告",
          desc: "汇总健康数据与测评结果，看清一段时间内的变化",
          path: "/user/report",
        },
      ],
      footerLinks: [
        { title: "我的收藏", path: "/user/my-save" },
        { title: "健康模型", path: "/user/user-health-model" },
        { title: "随访管理", path: "/user/followup" },
        { title: "个人中心", path: "/user/profile" },
      ],
    };
  },
  computed: {
    year() {
      return new Date().getFullYear();
    },
    /** 当前筛选的栏目名，用于「最新资讯」的副标题 */
    activeTagName() {
      if (this.newQueryDto.tagId === null || this.newQueryDto.tagId === undefined) {
        return "";
      }
      const hit = this.tagsList.find((t) => t.id === this.newQueryDto.tagId);
      return hit ? hit.name : "";
    },
    /**
     * 喂给知识网络图的词表。
     * 优先用真实栏目名；栏目不足 6 个时补上资讯自身的栏目名。
     * 两个都拿不到就返回空数组 —— 组件会用内置的健康主题词表兜底，
     * 而不是让首屏出现一块空白画布。
     */
    heroLabels() {
      const fromTags = this.tagsList.map((t) => t.name).filter(Boolean);
      if (fromTags.length >= 6) return fromTags;
      const fromNews = this.newsList.map((n) => n.tagName).filter(Boolean);
      return Array.from(new Set([...fromTags, ...fromNews]));
    },
  },
  created() {
    this.loadAllTags();
    this.loadAllNews();
    this.loadAllTopNews();
  },
  methods: {
    // 封面样式：有真实图片时显示图片，否则用栏目渐变色兜底
    coverStyle(news) {
      if (news && news.cover) {
        return {
          background: `url(${news.cover}) center / cover no-repeat`,
        };
      }
      const map = {
        康复手册: "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
        养生保健: "linear-gradient(135deg, #11998e 0%, #38ef7d 100%)",
        疾病预防: "linear-gradient(135deg, #ff6b6b 0%, #feca57 100%)",
        心理健康: "linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)",
        运动健身: "linear-gradient(135deg, #fa709a 0%, #fee140 100%)",
        饮食健康: "linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)",
      };
      return {
        background:
          map[news.tagName] ||
          "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
      };
    },
    /** 章节内跳转。开启「减少动态效果」时不走平滑滚动 —— 长距离滚动最易引发不适 */
    scrollTo(key) {
      const ref = this.$refs[key];
      const target = Array.isArray(ref) ? ref[0] : ref;
      if (!target) return;
      const el = target.$el || target;
      const reduce =
        typeof window.matchMedia === "function" &&
        window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      el.scrollIntoView({
        behavior: reduce ? "auto" : "smooth",
        block: "start",
      });
    },
    goFeature(path) {
      this.$router.push(path);
    },
    //
    onBannerClick(banner) {
      sessionStorage.setItem("newsInfo", JSON.stringify(banner));
      this.$router.push("/user/news-detail");
    },
    //
    newsItemClick(news) {
      sessionStorage.setItem("newsInfo", JSON.stringify(news));
      this.$router.push("/user/news-detail");
    },
    //
    parseTime(time) {
      return timeAgo(time);
    },
    tagOnClick(tags) {
      this.newQueryDto.tagId = tags.id;
      this.loadAllNews();
    },
    loadAllTags() {
      this.$axios
        .post("/tags/query", {})
        .then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.tagsList = data.data;
            this.tagsList.unshift({ name: "", id: null });
          }
        })
        .catch(() => {});
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
    loadAllNews() {
      this.$axios
        .post("/news/query", this.newQueryDto)
        .then((response) => {
          const { data } = response;
          if (data.code === 200) {
            this.newsList = data.data;
          }
        })
        .catch(() => {});
    },
  },
};
</script>

<style scoped lang="scss">
.home-container {
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  min-height: 100vh;
  /* 锚点跳转时给标题留出余量，否则落点顶在最上沿、看起来像没跳准 */
  scroll-behavior: smooth;
}

/* ---------------- 章节锚点条 ---------------- */
.site-nav {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 20px;
  margin-bottom: 20px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(8px);
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.06);
  box-sizing: border-box;
}

.site-nav__brand {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 1px;
  color: #3b3a63;
}

.site-nav__list {
  display: flex;
  align-items: center;
  gap: 4px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.site-nav__link {
  appearance: none;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  color: #5b6478;
  /* 项目里没有全局 box-sizing 重置，组件必须自己声明 */
  box-sizing: border-box;
  transition: background 0.25s ease, color 0.25s ease;

  &:hover {
    background: rgba(102, 126, 234, 0.12);
    color: #667eea;
  }

  &:focus-visible {
    outline: 2px solid #667eea;
    outline-offset: 2px;
  }
}

/* ---------------- 首屏 ---------------- */
.hero {
  position: relative;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 32px;
  align-items: center;
  padding: 44px 48px;
  margin-bottom: 24px;
  border-radius: 24px;
  overflow: hidden;
  color: #fff;
  box-sizing: border-box;
  background: radial-gradient(
      circle at 84% 16%,
      rgba(102, 126, 234, 0.55),
      transparent 56%
    ),
    linear-gradient(135deg, #1b2450 0%, #2b1f4a 55%, #3a1d47 100%);
  box-shadow: 0 24px 60px rgba(24, 30, 70, 0.3);
}

/* 网点底纹：官网首屏常见的质感层，纯装饰，不用图片省一次请求 */
.hero::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image: radial-gradient(
    rgba(255, 255, 255, 0.1) 1px,
    transparent 1px
  );
  background-size: 22px 22px;
  opacity: 0.7;
  pointer-events: none;
}

.hero__body,
.hero__visual {
  position: relative;
  z-index: 1;
}

.hero__eyebrow {
  margin: 0 0 14px;
  font-size: 13px;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.68);
}

.hero__title {
  margin: 0 0 18px;
  font-size: 38px;
  line-height: 1.28;
  font-weight: 800;
  background: linear-gradient(100deg, #ffffff 0%, #c7d2ff 60%, #f0c7ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: #fff;
}

.hero__desc {
  margin: 0 0 26px;
  max-width: 560px;
  font-size: 14px;
  line-height: 1.85;
  color: rgba(255, 255, 255, 0.76);
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  appearance: none;
  cursor: pointer;
  box-sizing: border-box;
  padding: 11px 24px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  transition: transform 0.25s ease, box-shadow 0.25s ease,
    background 0.25s ease;

  &:focus-visible {
    outline: 2px solid #fff;
    outline-offset: 3px;
  }
}

.btn--primary {
  border: none;
  color: #2a2350;
  background: #fff;
  box-shadow: 0 8px 24px rgba(255, 255, 255, 0.22);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 30px rgba(255, 255, 255, 0.32);
  }
}

.btn--ghost {
  border: 1px solid rgba(255, 255, 255, 0.38);
  background: rgba(255, 255, 255, 0.08);
  color: #fff;

  &:hover {
    background: rgba(255, 255, 255, 0.16);
    transform: translateY(-2px);
  }
}

.hero__note {
  margin: 22px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.hero__visual {
  position: relative;
}

.hero__visual-caption {
  margin: 6px 0 0;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.42);
}

/* ---------------- 章节通用 ---------------- */
.section {
  margin-bottom: 36px;
  /* 锚点落点补偿：跳转后标题不会贴到屏幕上沿 */
  scroll-margin-top: 16px;
}

.section-head {
  margin-bottom: 18px;
}

.section-head__title {
  position: relative;
  margin: 0 0 6px;
  padding-left: 14px;
  font-size: 20px;
  font-weight: 700;
  color: #2c3e50;

  &::before {
    content: "";
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 4px;
    height: 18px;
    border-radius: 2px;
    background: linear-gradient(180deg, #667eea, #764ba2);
  }
}

.section-head__desc {
  margin: 0;
  padding-left: 14px;
  font-size: 13px;
  color: #8a93a6;
}

/* ---------------- 服务入口 ---------------- */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 18px;
}

.feature-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  appearance: none;
  text-align: left;
  cursor: pointer;
  box-sizing: border-box;
  padding: 22px;
  border: 1px solid rgba(102, 126, 234, 0.1);
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  transition: transform 0.35s cubic-bezier(0.175, 0.885, 0.32, 1.275),
    box-shadow 0.35s ease, border-color 0.35s ease;

  &:hover {
    transform: translateY(-6px);
    border-color: rgba(102, 126, 234, 0.32);
    box-shadow: 0 14px 36px rgba(102, 126, 234, 0.18);
  }

  &:focus-visible {
    outline: 2px solid #667eea;
    outline-offset: 2px;
  }
}

.feature-card__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  font-size: 20px;
  color: #667eea;
  background: linear-gradient(
    135deg,
    rgba(102, 126, 234, 0.14),
    rgba(118, 75, 162, 0.14)
  );
}

.feature-card__title {
  font-size: 16px;
  font-weight: 700;
  color: #2c3e50;
}

.feature-card__desc {
  font-size: 13px;
  line-height: 1.7;
  color: #7b8497;
}

.feature-card__more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 4px;
  font-size: 13px;
  font-weight: 600;
  color: #667eea;
}

/* ---------------- 资讯卡片（沿用原有观感） ---------------- */
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
    content: "";
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

.cover-gradient {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;

  &::before {
    content: "";
    position: absolute;
    inset: 0;
    background: radial-gradient(
      circle at 30% 20%,
      rgba(255, 255, 255, 0.25) 0%,
      transparent 50%
    );
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
  background: linear-gradient(
    135deg,
    rgba(102, 126, 234, 0.15),
    rgba(118, 75, 162, 0.15)
  );
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
    content: "";
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
  padding: 12px;
  transition: background 0.3s ease;
}

/*
  标题：单行省略号。
  原文件里 .news-title 被定义了两遍，第二遍的 white-space: nowrap + text-overflow
  实际生效，但第一遍的 display: -webkit-box 还留着 —— 两者对不上，属于遗留的死声明。
  这里按**实际生效的观感**收敛成单行省略，删掉矛盾的 -webkit-box 那几行。
*/
.news-title {
  margin: 8px 0 4px 0;
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.news-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  margin-top: 8px;
}

/* ---------------- 页脚 ---------------- */
.site-footer {
  margin-top: 8px;
  border-radius: 20px;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.72);
  background: linear-gradient(135deg, #1e2440 0%, #2b2145 100%);
  box-sizing: border-box;
}

.site-footer__main {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr;
  gap: 28px;
  padding: 34px 36px 26px;
}

.site-footer__logo {
  display: block;
  margin-bottom: 10px;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 2px;
  color: #fff;
}

.site-footer__slogan {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.5);
}

.site-footer__links {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;

  li {
    font-size: 13px;
    cursor: pointer;
    transition: color 0.25s ease;

    &:hover {
      color: #fff;
    }
  }
}

.site-footer__meta {
  font-size: 12px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.5);

  p {
    margin: 0 0 14px;
  }
}

.site-footer__top {
  appearance: none;
  cursor: pointer;
  box-sizing: border-box;
  padding: 7px 16px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 999px;
  background: transparent;
  color: #fff;
  font-size: 12px;
  transition: background 0.25s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.12);
  }

  &:focus-visible {
    outline: 2px solid #fff;
    outline-offset: 2px;
  }
}

.site-footer__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 36px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.42);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1280px) {
  .hero {
    grid-template-columns: 1fr;
    padding: 36px 32px;
  }

  .hero__title {
    font-size: 30px;
  }

  /* 单列时网络图挤在文字下方会占掉大半屏，留个更矮的版本 */
  .hero__visual :deep(.net-wrap) {
    height: 220px;
  }
}

@media (max-width: 900px) {
  .site-nav {
    flex-wrap: wrap;
    gap: 10px;
  }

  .site-footer__main {
    grid-template-columns: 1fr;
    gap: 20px;
    padding: 26px 22px;
  }

  .site-footer__bar {
    padding: 14px 22px;
    flex-direction: column;
    gap: 6px;
  }
}

/* ---------------- 深色模式 ---------------- */
/*
  页面自己的底色、白卡片、页脚都要跟着深色走。
  用 html.dark 作祖先选择器（而不是 :deep(.dark)）—— :deep 编译后要求 .dark
  是本组件的**后代**，而 html.dark 在文档最外层，那样写恒不命中。
*/
html.dark .home-container {
  background: linear-gradient(135deg, #14161c 0%, #1b1e26 100%);
}

html.dark .site-nav {
  background: rgba(30, 33, 42, 0.9);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.4);
}

html.dark .site-nav__brand {
  color: #e6e8f0;
}

html.dark .site-nav__link {
  color: #9aa3b8;

  &:hover {
    background: rgba(102, 126, 234, 0.22);
    color: #b9c2ff;
  }
}

html.dark .section-head__title {
  color: #e6e8f0;
}

html.dark .section-head__desc {
  color: #7d8698;
}

html.dark .feature-card,
html.dark .top-news-card,
html.dark .news-card {
  background: #1e212a;
  border-color: rgba(255, 255, 255, 0.07);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
}

html.dark .feature-card__title,
html.dark .news-title {
  color: #e6e8f0;
}

html.dark .feature-card__desc {
  color: #8d96a8;
}

html.dark .news-card:hover .news-card-body {
  background: linear-gradient(135deg, #23273a 0%, #1e212a 100%);
}

html.dark .news-time {
  color: #7d8698;
}
</style>
