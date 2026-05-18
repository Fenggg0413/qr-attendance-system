package com.example.qrattendance.qr;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// QR 签到 Token 服务：基于 HMAC-SHA256 + 10 秒时间桶生成动态 Token
// 设计目的：Token 每 10 秒自动滚动，截图/转发的旧码会立即失效；服务端不需存 Token，按 sessionId+bucket 重算即可校验
@Service
public class QrTokenService {
  // 时间桶宽度：每 10 秒一桶，决定 Token 的滚动周期
  public static final long BUCKET_SECONDS = 10;
  // HMAC 密钥（与 JWT 独立，从 app.qr.secret 注入）
  private final byte[] secret;
  // 时钟抽象，方便测试注入固定时间
  private final Clock clock;

  @Autowired
  public QrTokenService(@Value("${app.qr.secret}") String secret) {
    this(secret, Clock.systemUTC());
  }

  QrTokenService(String secret, Clock clock) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.clock = clock;
  }

  // 取当前桶的 Token 快照，并附带其失效时刻（下一个桶起点）和扫码 payload
  public TokenSnapshot current(long sessionId) {
    long now = clock.instant().getEpochSecond();
    // 把 UNIX 秒整除以桶宽，得到桶序号
    long bucket = now / BUCKET_SECONDS;
    // 下一桶开始时刻 = 当前桶失效时刻
    long expiresEpoch = (bucket + 1) * BUCKET_SECONDS;
    String token = tokenFor(sessionId, bucket);
    // Android 端扫到的 URL 由 QrPayloadParser 解析，提取 sessionId 与 token
    String payload = "qr-attendance://checkin?sessionId=" + sessionId + "&token=" + token;
    return new TokenSnapshot(sessionId, token, Instant.ofEpochSecond(expiresEpoch), payload);
  }

  // 校验扫码上传的 Token 是否为"当前桶"生成的
  // 注意：只接受当前桶，不接受上一桶 —— 严格 10 秒窗口；客户端时钟漂移过大会失败
  public boolean acceptsCurrent(long sessionId, String token) {
    long bucket = clock.instant().getEpochSecond() / BUCKET_SECONDS;
    // 服务端用当前桶重算 Token 与上传 Token 做常时间比较
    return constantTimeEquals(tokenFor(sessionId, bucket), token);
  }

  // 对 (sessionId, bucket) 做 HMAC-SHA256 生成 Token；sessionId 绑定具体考勤场次，bucket 实现时间滚动
  public String tokenFor(long sessionId, long bucket) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      // 消息体格式："sessionId:bucket"，简单拼接即可
      String message = sessionId + ":" + bucket;
      byte[] hmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      // Base64 URL 编码（无填充），便于嵌入 URL 不需 % 转义
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot generate QR token", ex);
    }
  }

  // 常时间字符串比较，防止时序攻击（实现同 JwtService.constantTimeEquals）
  private boolean constantTimeEquals(String left, String right) {
    if (left == null || right == null || left.length() != right.length()) return false;
    int result = 0;
    // 即使首字节就不同，也走完整个循环；保证比较耗时与字符串长度成正比、与差异位置无关
    for (int i = 0; i < left.length(); i++) result |= left.charAt(i) ^ right.charAt(i);
    return result == 0;
  }

  // Token 快照：包含 sessionId、Token 本体、失效时刻、扫码 payload；返回给教师端用于渲染二维码
  public record TokenSnapshot(long sessionId, String token, Instant expiresAt, String payload) {}
}
