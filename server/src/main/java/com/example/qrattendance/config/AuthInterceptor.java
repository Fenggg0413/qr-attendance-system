package com.example.qrattendance.config;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.auth.CurrentUser;
import com.example.qrattendance.auth.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;

// 鉴权拦截器：在所有 /api/** 请求进入控制器前提取 Bearer Token 并解析为 CurrentUser
// 解析成功后写入 AuthContext，供控制器内 admin()/teacher()/student() 等方法读取
@Component
public class AuthInterceptor implements HandlerInterceptor {
  private final JwtService jwtService;

  public AuthInterceptor(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  // 请求进入控制器前执行；返回 true 才会继续派发到控制器方法
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    // 白名单放行：CORS 预检（OPTIONS）和登录接口本身（登录前无 Token，自然无法鉴权）
    if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || request.getRequestURI().equals("/api/auth/login")) {
      return true;
    }
    // Authorization 头必须形如 "Bearer xxxx"
    String auth = request.getHeader("Authorization");
    if (auth == null || !auth.startsWith("Bearer ")) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
    }
    // 截掉 "Bearer " 前缀后交给 JwtService 验签并解析；签名错误/过期会返回 null
    CurrentUser user = jwtService.verify(auth.substring("Bearer ".length()));
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token");
    }
    // 解析成功，写入 ThreadLocal，控制器即可通过 AuthContext.require()/requireRole() 读取
    AuthContext.set(user);
    return true;
  }

  // 请求完整结束后（无论成功失败）清理 ThreadLocal，避免线程复用导致用户串扰
  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    AuthContext.clear();
  }
}
