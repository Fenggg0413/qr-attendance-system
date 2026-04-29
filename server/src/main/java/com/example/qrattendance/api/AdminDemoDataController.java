package com.example.qrattendance.api;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.demo.DemoDataService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/demo-data")
public class AdminDemoDataController {
  private final DemoDataService demoDataService;

  public AdminDemoDataController(DemoDataService demoDataService) {
    this.demoDataService = demoDataService;
  }

  @PostMapping("/reset")
  public Map<String, Object> reset(@RequestBody(required = false) Map<String, Object> body) {
    var admin = AuthContext.requireRole("ADMIN");
    String preset = body == null ? "medium" : String.valueOf(body.getOrDefault("preset", "medium"));
    return demoDataService.reset(preset, admin);
  }
}
