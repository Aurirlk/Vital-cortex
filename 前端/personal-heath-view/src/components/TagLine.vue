<template>
  <div>
    <span v-for="(entity, index) in dataList" :key="index">
      <span
        class="tag-item"
        :style="{
          background:
            tagSelected.name === entity.name
              ? 'linear-gradient(135deg, #667eea, #764ba2)'
              : 'transparent',
          color: tagSelected.name === entity.name ? '#FFFFFF' : '#4a5568',
          borderColor:
            tagSelected.name === entity.name ? 'transparent' : '#e8ecf4',
          boxShadow:
            tagSelected.name === entity.name
              ? '0 4px 12px rgba(102, 126, 234, 0.3)'
              : 'none',
        }"
        @click="onClick(entity)"
      >
        {{ entity.name }}
      </span>
    </span>
  </div>
</template>

<script>
// 标签渲染组件，接收渲染参数，点击后会向父组件反馈
export default {
  name: "TagLine",
  data() {
    return {
      tagSelected: {},
    };
  },
  props: {
    dataList: {
      type: Array,
      required: true,
    },
  },
  mounted() {
    this.onClick({ name: "全部", id: null });
  },
  methods: {
    onClick(tag) {
      this.tagSelected = tag;
      this.$emit("on-click", tag);
    },
    all() {
      this.$emit("on-click", { id: null, name: "全部" });
    },
  },
};
</script>

<style scoped lang="scss">
.tag-item {
  font-size: 13px;
  display: inline-block;
  margin: 8px 8px 8px 0;
  padding: 6px 16px;
  cursor: pointer;
  user-select: none;
  border-radius: 20px;
  transition: all 0.3s ease;
  font-weight: 500;
  border: 1.5px solid #e8ecf4;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
  }
}
</style>
