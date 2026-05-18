package com.example.qrattendance.data.qr

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// 扫码后从二维码原文解析出的签到载荷：sessionId 标识签到会话，token 是 10s 内有效的 HMAC 短令牌。
data class QrPayload(val sessionId: Long, val token: String)

// 解析服务端约定的二维码协议 `qr-attendance://checkin?sessionId=<id>&token=<token>`。
object QrPayloadParser {
  // 任一关键字段缺失或协议不符都返 null，由调用方在 UI 上提示"无效二维码"。
  fun parse(raw: String): QrPayload? {
    // runCatching 包住 URI 构造，防止非法字符串抛 URISyntaxException 让分析器崩溃。
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    // 强校验自定义 scheme/host，避免把普通 URL 二维码误识别为签到码。
    if (uri.scheme != "qr-attendance" || uri.host != "checkin") return null
    // 手动 split + URLDecoder：避免依赖 Android 的 Uri.getQueryParameter（JVM 单测中不可用）。
    val params = uri.rawQuery.orEmpty()
      .split('&')
      .filter { it.contains('=') }
      .associate {
        val key = it.substringBefore('=')
        val value = URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8.name())
        key to value
      }
    val sessionId = params["sessionId"]?.toLongOrNull() ?: return null
    val token = params["token"].orEmpty()
    if (token.isBlank()) return null
    return QrPayload(sessionId, token)
  }
}
