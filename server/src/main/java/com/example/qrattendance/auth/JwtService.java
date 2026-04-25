package com.example.qrattendance.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private final ObjectMapper mapper;
  private final byte[] secret;

  public JwtService(ObjectMapper mapper, @Value("${app.jwt.secret}") String secret) {
    this.mapper = mapper;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public String issue(CurrentUser user) {
    try {
      Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
      Map<String, Object> claims = new LinkedHashMap<>();
      claims.put("sub", user.id());
      claims.put("username", user.username());
      claims.put("role", user.role());
      claims.put("displayName", user.displayName());
      claims.put("exp", Instant.now().plusSeconds(12 * 60 * 60).getEpochSecond());
      String body = encode(mapper.writeValueAsBytes(header)) + "." + encode(mapper.writeValueAsBytes(claims));
      return body + "." + sign(body);
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot issue token", ex);
    }
  }

  public CurrentUser verify(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) return null;
      String body = parts[0] + "." + parts[1];
      if (!constantTimeEquals(sign(body), parts[2])) return null;
      Map<String, Object> claims =
          mapper.readValue(DECODER.decode(parts[1]), new TypeReference<Map<String, Object>>() {});
      long exp = ((Number) claims.get("exp")).longValue();
      if (Instant.now().getEpochSecond() > exp) return null;
      return new CurrentUser(
          ((Number) claims.get("sub")).longValue(),
          (String) claims.get("username"),
          (String) claims.get("role"),
          (String) claims.get("displayName"));
    } catch (Exception ex) {
      return null;
    }
  }

  private String encode(byte[] value) {
    return ENCODER.encodeToString(value);
  }

  private String sign(String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    return encode(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }

  private boolean constantTimeEquals(String left, String right) {
    if (left.length() != right.length()) return false;
    int result = 0;
    for (int i = 0; i < left.length(); i++) result |= left.charAt(i) ^ right.charAt(i);
    return result == 0;
  }
}
