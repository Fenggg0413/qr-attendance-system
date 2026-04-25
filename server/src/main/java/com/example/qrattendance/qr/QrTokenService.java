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

@Service
public class QrTokenService {
  public static final long BUCKET_SECONDS = 10;
  private final byte[] secret;
  private final Clock clock;

  @Autowired
  public QrTokenService(@Value("${app.qr.secret}") String secret) {
    this(secret, Clock.systemUTC());
  }

  QrTokenService(String secret, Clock clock) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.clock = clock;
  }

  public TokenSnapshot current(long sessionId) {
    long now = clock.instant().getEpochSecond();
    long bucket = now / BUCKET_SECONDS;
    long expiresEpoch = (bucket + 1) * BUCKET_SECONDS;
    String token = tokenFor(sessionId, bucket);
    String payload = "qr-attendance://checkin?sessionId=" + sessionId + "&token=" + token;
    return new TokenSnapshot(sessionId, token, Instant.ofEpochSecond(expiresEpoch), payload);
  }

  public boolean acceptsCurrent(long sessionId, String token) {
    long bucket = clock.instant().getEpochSecond() / BUCKET_SECONDS;
    return constantTimeEquals(tokenFor(sessionId, bucket), token);
  }

  public String tokenFor(long sessionId, long bucket) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      String message = sessionId + ":" + bucket;
      byte[] hmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot generate QR token", ex);
    }
  }

  private boolean constantTimeEquals(String left, String right) {
    if (left == null || right == null || left.length() != right.length()) return false;
    int result = 0;
    for (int i = 0; i < left.length(); i++) result |= left.charAt(i) ^ right.charAt(i);
    return result == 0;
  }

  public record TokenSnapshot(long sessionId, String token, Instant expiresAt, String payload) {}
}
