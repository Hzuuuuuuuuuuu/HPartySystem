package com.hparty.framework.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.hparty.common.core.R;
import com.hparty.common.core.ResultCode;
import com.hparty.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把所有异常收敛成统一响应体，避免堆栈泄漏到前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常 [{}] {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 未登录 */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e, HttpServletRequest request) {
        log.warn("未登录访问 {}: {}", request.getRequestURI(), e.getMessage());
        String msg = switch (e.getType()) {
            case NotLoginException.NOT_TOKEN -> "未提供登录凭证，请先登录";
            case NotLoginException.INVALID_TOKEN -> "登录凭证无效，请重新登录";
            case NotLoginException.TOKEN_TIMEOUT -> "登录已过期，请重新登录";
            case NotLoginException.BE_REPLACED -> "账号已在其他设备登录";
            case NotLoginException.KICK_OUT -> "账号已被强制下线";
            default -> ResultCode.UNAUTHORIZED.getMsg();
        };
        return R.fail(ResultCode.UNAUTHORIZED.getCode(), msg);
    }

    /** 无权限 */
    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermission(NotPermissionException e, HttpServletRequest request) {
        log.warn("越权访问 {}: 缺少权限 {}", request.getRequestURI(), e.getPermission());
        return R.fail(ResultCode.FORBIDDEN, "没有操作权限：" + e.getPermission());
    }

    /** 无角色 */
    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException e, HttpServletRequest request) {
        log.warn("越权访问 {}: 缺少角色 {}", request.getRequestURI(), e.getRole());
        return R.fail(ResultCode.FORBIDDEN, "没有操作权限，需要角色：" + e.getRole());
    }

    /** @RequestBody 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return R.fail(ResultCode.PARAM_ERROR.getCode(), msg.isEmpty() ? ResultCode.PARAM_ERROR.getMsg() : msg);
    }

    /** 表单参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return R.fail(ResultCode.PARAM_ERROR.getCode(), msg.isEmpty() ? ResultCode.PARAM_ERROR.getMsg() : msg);
    }

    /** 方法参数约束校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("；"));
        return R.fail(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ResultCode.PARAM_ERROR.getCode(), "缺少必填参数：" + e.getParameterName());
    }

    /** 参数类型不匹配 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail(ResultCode.PARAM_ERROR.getCode(), "参数类型不正确：" + e.getName());
    }

    /** 请求体格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return R.fail(ResultCode.PARAM_ERROR.getCode(), "请求体格式不正确");
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(ResultCode.METHOD_NOT_ALLOWED.getCode(), "不支持的请求方法：" + e.getMethod());
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{}] {}", request.getMethod(), request.getRequestURI(), e);
        return R.fail(ResultCode.FAIL.getCode(), "系统内部错误，请联系管理员");
    }
}
