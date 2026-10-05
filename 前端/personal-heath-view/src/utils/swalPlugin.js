import Swal from "sweetalert2";

const swalPlugin = {
  install(app) {
    app.config.globalProperties.$swalConfirm = async function (options = {}) {
      const defaultOptions = {
        title: "",
        text: "",
        icon: "info",
        reverseButtons: true,
        showCancelButton: true,
        // 注意：这两个文案不能为空串，否则确认/取消按钮会渲染成无文字的空按钮
        // （原项目此处文案因编码问题被清空，导致弹窗按钮只剩色块，已修复）
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        confirmButtonAriaLabel: "确定",
        cancelButtonAriaLabel: "取消",
        ...options,
      };

      try {
        const result = await Swal.fire(defaultOptions);
        return result.isConfirmed;
      } catch (error) {
        console.error("Swal Error:", error);
        return false;
      }
    };
  },
};

export default swalPlugin;
