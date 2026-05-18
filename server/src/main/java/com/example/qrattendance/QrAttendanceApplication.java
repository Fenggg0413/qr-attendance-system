package com.example.qrattendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring Boot 应用入口；启动后 DatabaseInitializer 会自动初始化 SQLite 表与默认管理员
@SpringBootApplication
public class QrAttendanceApplication {
  // 启动应用，监听 application.properties 配置的端口（默认 8080）
  public static void main(String[] args) {
    SpringApplication.run(QrAttendanceApplication.class, args);
  }
}
