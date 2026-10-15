package cn.kmbeast.config;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理所有未捕获的异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 实际生效的上传上限（各 profile 可覆盖），仅在超限提示里回显。 */
    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private String maxFileSize = "10MB";

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return ApiResult.error(message);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return ApiResult.error(message);
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("约束违反: {}", message);
        return ApiResult.error(message);
    }

    /**
     * 处理数字格式异常
     */
    @ExceptionHandler(NumberFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleNumberFormatException(NumberFormatException e) {
        log.warn("数字格式错误: {}", e.getMessage());
        return ApiResult.error("参数格式错误");
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return ApiResult.error(e.getMessage());
    }

    /**
     * 处理CRM业务异常
     */
    @ExceptionHandler(cn.kmbeast.crm.CrmException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleCrmException(cn.kmbeast.crm.CrmException e) {
        log.warn("CRM异常 [{}]: {}", e.getErrorCode(), e.getMessage());
        return ApiResult.error(e.getMessage());
    }

    /**
     * 处理上传体积超限。
     *
     * <p>阶段 2 新增：知识库支持上传 PDF/Word/Excel 之后才第一次有真实的
     * 大文件上传路径。此前本异常会落到下面的兜底分支，用户只看到
     * 「系统异常，请稍后重试（追踪ID: …）」——一个<b>他改不了也看不懂</b>的提示。
     * 这里要说的只有两件事：多大算大、怎么办。</p>
     *
     * <p>上限值从配置读：各 profile 的 {@code spring.servlet.multipart.max-file-size}
     * 可能不同，写死一个数字会在改配置后立刻变成假话。</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过上限: {}", e.getMessage());
        return ApiResult.error("文件超过上传上限（当前 " + maxFileSize
                + "），请压缩或拆分后重试。也可调整 spring.servlet.multipart.max-file-size 与 "
                + "crm.rag.parse.max-bytes（两者需保持一致）。");
    }

    /**
     * 处理所有其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        String traceId = MDC.get("traceId");
        Integer userId = LocalThreadHolder.getUserId();
        String uri = request != null ? request.getRequestURI() : "unknown";
        log.error("系统异常 uri={} userId={} traceId={}", uri, userId, traceId, e);
        String msg = traceId != null
                ? "系统异常，请稍后重试（追踪ID: " + traceId + "）"
                : "系统异常，请稍后重试";
        return ApiResult.error(msg);
    }
}
