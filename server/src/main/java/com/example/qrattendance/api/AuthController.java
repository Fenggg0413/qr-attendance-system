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

@RestController
@RequestMapping("/api")
public class AuthController {
  private final JdbcTemplate jdbc;
  private final JwtService jwtService;

  public AuthController(JdbcTemplate jdbc, JwtService jwtService) {
    this.jdbc = jdbc;
    this.jwtService = jwtService;
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@RequestBody LoginRequest request) {
    CurrentUser user =
        jdbc.query(
                "SELECT id, username, role, display_name FROM users WHERE username = ? AND password_hash = ?",
                (rs, row) -> new CurrentUser(rs.getLong("id"), rs.getString("username"), rs.getString("role"), rs.getString("display_name")),
                request.username(),
                PasswordHasher.hash(request.password()))
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
    return Map.of("token", jwtService.issue(user), "user", user);
  }

  @GetMapping("/me")
  public CurrentUser me() {
    return AuthContext.require();
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
