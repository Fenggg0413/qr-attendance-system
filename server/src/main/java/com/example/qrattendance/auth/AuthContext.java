package com.example.qrattendance.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 请求级别的当前用户上下文，基于 ThreadLocal 实现
// 由 AuthInterceptor.preHandle 写入、afterCompletion 清理；控制器通过 require/requireRole 读取
public final class AuthContext {
  // ThreadLocal 保证多线程并发请求之间互不干扰
  private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

  private AuthContext() {}

  // 写入当前请求的已认证用户（由 AuthInterceptor 调用）
  public static void set(CurrentUser user) {
    CURRENT.set(user);
  }

  // 清理 ThreadLocal，防止线程复用时泄漏到下一个请求（务必在 afterCompletion 调用）
  public static void clear() {
    CURRENT.remove();
  }

  // 取当前用户；未登录直接抛 401（被 ApiExceptionHandler 转成 JSON 响应）
  public static CurrentUser require() {
    CurrentUser user = CURRENT.get();
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
    return user;
  }

  // 取当前用户并校验角色；角色不符抛 403（控制器内 admin()/teacher()/student() 都基于此）
  public static CurrentUser requireRole(String role) {
    CurrentUser user = require();
    if (!role.equals(user.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }
    return user;
  }
}
