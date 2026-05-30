<template>
  <div>
    <Toolbar
      style="border-bottom: 1px solid #eae8e8"
      :editor="editor"
      :defaultConfig="toolbarConfig"
      :mode="mode"
    />
    <Editor
      :style="{ height: height, overflowY: 'hidden' }"
      v-model="content"
      :defaultConfig="editorConfig"
      :mode="mode"
      @onCreated="onCreated"
    />
  </div>
</template>
<script>
import { Editor, Toolbar } from "@wangeditor/editor-for-vue";
import { URL_API } from "@/utils/request";
export default {
  components: { Editor, Toolbar },
  props: {
    receiveContent: {
      type: String,
      default: "",
      required: true,
    },
    height: {
      type: String,
      default: "calc(100vh - 100px)",
    },
  },
  data() {
    return {
      editor: null,
      content: "<p>创作内容</p>",
      toolbarConfig: {},
      editorConfig: {
        placeholder: "请输入内容...",
        MENU_CONF: {
          uploadImage: {
            server: URL_API + "/file/upload",
            fieldName: "file",
            maxFileSize: 10 * 1024 * 1024,
            maxNumberOfFiles: 10,
            allowedFileTypes: ["image/*"],
            metaWithUrl: false,
            withCredentials: true,
            timeout: 10 * 1000,
            headers: {
              token: sessionStorage.getItem("token"),
            },
            customInsert(res, insertFn) {
              insertFn(res.data, res.data, res.data);
            },
          },
        },
      },
      mode: "default",
    };
  },
  methods: {
    onCreated(editor) {
      this.editor = Object.seal(editor);
      // 上传视频功能排除
      this.toolbarConfig.excludeKeys = ["group-video", "group-image"];
    },
  },
  watch: {
    receiveContent: {
      handler(v1, v2) {
        console.log("接收内容：", v1);
        this.content = v1;
      },
      deep: true, // 启用深度监听
      immediate: true,
    },
    content(newVal, oldVal) {
      this.$emit("on-receive", newVal);
    },
  },
  beforeUnmount() {
    const editor = this.editor;
    if (editor == null) return;
    editor.destroy();
  },
};
</script>
<style src="@wangeditor/editor/dist/css/style.css"></style>
<style scoped>
.line-number {
  display: block;
  margin-right: 10px;
  /* 以下样式确保行号不被选中或复制 */
  pointer-events: none;
  user-select: none;
  -webkit-user-select: none;
  color: #999;
  /* 行号颜色，可自定义 */
  text-align: right;
  /* 行号对齐方式 */
}
</style>
