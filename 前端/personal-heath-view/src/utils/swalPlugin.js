import Swal from "sweetalert2";

const swalPlugin = {
  install(app) {
    app.config.globalProperties.$swalConfirm = async function (options = {}) {
      const defaultOptions = {
        title: "提示",
        text: "",
        icon: "info",
        reverseButtons: true,
        showCancelButton: true,
        confirmButtonText: "确认",
        cancelButtonText: "取消",
        customClass: {
          confirmButton: "sweet-btn-primary",
        },
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
