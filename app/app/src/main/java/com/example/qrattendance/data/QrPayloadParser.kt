package com.example.qrattendance.data

import java.net.URLDecoder
import java.net.URI
import java.nio.charset.StandardCharsets

object QrPayloadParser {
  fun parse(payload: String): CheckInRequest? {
    return runCatching {
        val uri = URI(payload.trim())
        if (uri.scheme != "qr-attendance" || uri.host != "checkin") return null
        val params =
          uri.rawQuery
            ?.split("&")
            ?.mapNotNull {
              val parts = it.split("=", limit = 2)
              if (parts.size == 2) parts[0] to parts[1] else null
            }
            ?.toMap()
            ?: return null
        val sessionId = params["sessionId"]?.toLongOrNull() ?: return null
        val token = params["token"]?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }?.takeIf { it.isNotBlank() } ?: return null
        CheckInRequest(sessionId, token)
      }
      .getOrNull()
  }
}
