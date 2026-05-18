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

// 自实现的 JWT 服务（HS256），不引入第三方 JWT 库
// Token 三段式：base64url(header).base64url(claims).base64url(HMAC-SHA256(header.claims))
@Service
public class JwtService {
  // Base64 URL 编码器（无填充 =，符合 JWT 规范）
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private final ObjectMapper mapper;
  // HMAC 密钥（从 application.properties 的 app.jwt.secret 注入）
  private final byte[] secret;

  public JwtService(ObjectMapper mapper, @Value("${app.jwt.secret}") String secret) {
    this.mapper = mapper;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  // 为登录成功的用户颁发 JWT；有效期 12 小时
  public String issue(CurrentUser user) {
    try {
      // header：声明算法 HS256 与类型 JWT
      Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
      // claims：身份信息 + exp 过期时间戳（UNIX 秒）
      Map<String, Object> claims = new LinkedHashMap<>();
      claims.put("sub", user.id());
      claims.put("username", user.username());
      claims.put("role", user.role());
      claims.put("displayName", user.displayName());
      // exp 设为当前时间 + 12 小时（按秒计），由 verify 检查是否过期
      claims.put("exp", Instant.now().plusSeconds(12 * 60 * 60).getEpochSecond());
      // 先拼出 header.claims，再对它整体 HMAC 签名，得到第三段
      String body = encode(mapper.writeValueAsBytes(header)) + "." + encode(mapper.writeValueAsBytes(claims));
      return body + "." + sign(body);
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot issue token", ex);
    }
  }

  // 验证 Token 合法性：格式 + 签名 + 过期；任一步失败返回 null（不抛异常，交给拦截器统一处理）
  public CurrentUser verify(String token) {
    try {
      String[] parts = token.split("\\.");
      // 必须严格三段（header.claims.signature）
      if (parts.length != 3) return null;
      // 重算 body 部分（header.claims），并与原签名比对
      String body = parts[0] + "." + parts[1];
      // 常时间比较，防止时序攻击逐字节猜测签名
      if (!constantTimeEquals(sign(body), parts[2])) return null;
      // Base64 URL 解码 claims 部分，反序列化为 Map
      Map<String, Object> claims =
          mapper.readValue(DECODER.decode(parts[1]), new TypeReference<Map<String, Object>>() {});
      long exp = ((Number) claims.get("exp")).longValue();
      // 当前时间已超过 exp 视为过期
      if (Instant.now().getEpochSecond() > exp) return null;
      return new CurrentUser(
          ((Number) claims.get("sub")).longValue(),
          (String) claims.get("username"),
          (String) claims.get("role"),
          (String) claims.get("displayName"));
    } catch (Exception ex) {
      // 任何解析异常一律视为无效 Token
      return null;
    }
  }

  // Base64 URL 编码，无填充
  private String encode(byte[] value) {
    return ENCODER.encodeToString(value);
  }

  // 对 body（header.claims）做 HMAC-SHA256 签名，输出 Base64 URL
  private String sign(String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    // 用 UTF-8 字节的 secret 初始化 HMAC
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    return encode(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }

  // 常时间字符串比较：逐字节异或累积到 result，避免遇到首个不等字节就提前返回
  // —— 防止攻击者通过响应时间差异逐位猜测签名（timing attack）
  private boolean constantTimeEquals(String left, String right) {
    if (left.length() != right.length()) return false;
    int result = 0;
    // 用 |= 累积异或差异：只要有任何一位不同，result 最终非 0
    for (int i = 0; i < left.length(); i++) result |= left.charAt(i) ^ right.charAt(i);
    return result == 0;
  }
}
