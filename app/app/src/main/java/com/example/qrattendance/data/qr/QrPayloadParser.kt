package com.example.qrattendance.data.qr

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class QrPayload(val sessionId: Long, val token: String)

object QrPayloadParser {
  fun parse(raw: String): QrPayload? {
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    if (uri.scheme != "qr-attendance" || uri.host != "checkin") return null
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
