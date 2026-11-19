package com.checkdata.agent.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：把异常统一转成 R 响应体。
 *
 * - NotLoginException → 401（前端收到后跳登录页）
 * - NotRoleException  → 403（无权限访问）
 * - IllegalArgumentException → 400（参数错误）
 * - 兜底 Exception → 500
 *
 * 注意：流式接口（SSE）的异常不经过 ControllerAdvice，由 Reactor 的 onError 信号传递，
 * 前端在 SSE 流的 Error 事件里处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<R<Void>> handleNotLogin(NotLoginException e) {
        // HTTP 401 让前端请求拦截器统一处理（跳转登录页）
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(R.unauthorized("未登录或登录已过期"));
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<R<Void>> handleNotRole(NotRoleException e) {
        log.warn("无权限访问：requiredRole={}, type={}", e.getRole(), e.getLoginType());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.forbidden("无权限访问"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArg(IllegalArgumentException e) {
        return R.paramError(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public R<Void> handleIllegalState(IllegalStateException e) {
        return R.fail(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail(e.getMessage());
    }
}
