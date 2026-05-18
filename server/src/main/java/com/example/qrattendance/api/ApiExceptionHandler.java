package com.example.qrattendance.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

// 全局异常处理器：把控制器抛出的 ResponseStatusException 转成统一 JSON 响应 {message, error}
@RestControllerAdvice
public class ApiExceptionHandler {
  // 拦截所有 ResponseStatusException：原因为空则用状态码文本兜底（如"401 UNAUTHORIZED"）
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException err) {
    String message = err.getReason() == null || err.getReason().isBlank() ? err.getStatusCode().toString() : err.getReason();
    // message 给前端展示用，error 字段冗余保留以兼容旧客户端
    return ResponseEntity.status(err.getStatusCode()).body(Map.of("message", message, "error", message));
  }
}
