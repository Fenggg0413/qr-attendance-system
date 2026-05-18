package com.example.qrattendance.api;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.demo.DemoDataService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 管理端：演示数据重置入口（仅 ADMIN 可调用，对应 scripts/reset-campus-demo.mjs 脚本）
@RestController
@RequestMapping("/api/admin/demo-data")
public class AdminDemoDataController {
  private final DemoDataService demoDataService;

  public AdminDemoDataController(DemoDataService demoDataService) {
    this.demoDataService = demoDataService;
  }

  // 清空业务数据并按 preset (small/medium) 重新生成校园演示数据；返回各类实体的创建数量
  @PostMapping("/reset")
  public Map<String, Object> reset(@RequestBody(required = false) Map<String, Object> body) {
    // 强制 ADMIN 角色，非管理员调用直接 403
    var admin = AuthContext.requireRole("ADMIN");
    // 未传 preset 时默认用 medium（中等规模，约 60 教师 / 720 学生）
    String preset = body == null ? "medium" : String.valueOf(body.getOrDefault("preset", "medium"));
    return demoDataService.reset(preset, admin);
  }
}
