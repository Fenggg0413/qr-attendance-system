package com.example.qrattendance.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Web MVC 全局配置：注册认证拦截器 + 配置 CORS（供 Web 前端、Android 客户端跨域访问）
@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AuthInterceptor authInterceptor;
  // 允许跨域的来源列表，从 app.cors.allowed-origins 注入（逗号分隔）
  private final List<String> allowedOrigins;

  public WebConfig(AuthInterceptor authInterceptor, @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
    this.authInterceptor = authInterceptor;
    this.allowedOrigins = allowedOrigins;
  }

  // 将 AuthInterceptor 挂到所有 /api/** 路径下，统一鉴权（登录接口在拦截器内部白名单放行）
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
  }

  // 配置 /api/** 的 CORS：允许配置中列出的来源、所有方法、所有头部
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**").allowedOrigins(allowedOrigins.toArray(String[]::new)).allowedMethods("*").allowedHeaders("*");
  }
}
