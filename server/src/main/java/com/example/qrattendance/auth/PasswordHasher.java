package com.example.qrattendance.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

// 密码哈希工具：SHA-256 + 十六进制输出
// 注意：未加盐、未做 KDF 慢化（PBKDF2/bcrypt 等），仅适用于课程演示，不适合生产环境
public final class PasswordHasher {
  private PasswordHasher() {}

  // 对明文密码做 SHA-256 摘要，并以十六进制小写字符串返回（用于存入 users.password_hash 列）
  public static String hash(String raw) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      // UTF-8 字节 → SHA-256 摘要（32 字节） → 64 位十六进制字符串
      return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot hash password", ex);
    }
  }
}
