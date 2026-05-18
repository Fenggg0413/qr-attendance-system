package com.example.qrattendance.api;

import com.example.qrattendance.auth.AuthContext;
import com.example.qrattendance.auth.CurrentUser;
import com.example.qrattendance.auth.JwtService;
import com.example.qrattendance.auth.PasswordHasher;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 认证控制器：负责登录颁发 Token 以及查询当前用户
@RestController
@RequestMapping("/api")
public class AuthController {
  private final JdbcTemplate jdbc;
  private final JwtService jwtService;

  public AuthController(JdbcTemplate jdbc, JwtService jwtService) {
    this.jdbc = jdbc;
    this.jwtService = jwtService;
  }

  // 用户名 + 密码登录；成功返回 { token, user }，失败统一 401（不区分用户名错误/密码错误，防止账号枚举）
  @PostMapping("/auth/login")
  public Map<String, Object> login(@RequestBody LoginRequest request) {
    // 直接用 username 和 SHA-256(password) 联合查询，命中即视为认证通过；不在内存比对密码避免明文驻留
    CurrentUser user =
        jdbc.query(
                "SELECT id, username, role, display_name FROM users WHERE username = ? AND password_hash = ?",
                (rs, row) -> new CurrentUser(rs.getLong("id"), rs.getString("username"), rs.getString("role"), rs.getString("display_name")),
                request.username(),
                PasswordHasher.hash(request.password()))
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
    // 颁发 12 小时有效的 JWT Token，前端存 localStorage 后续请求带 Authorization 头
    return Map.of("token", jwtService.issue(user), "user", user);
  }

  // 查询当前已登录用户信息（前端刷新时用于恢复会话状态）
  @GetMapping("/me")
  public CurrentUser me() {
    return AuthContext.require();
  }

  // 登录请求体；@NotBlank 保证两个字段都非空、非空白
  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
