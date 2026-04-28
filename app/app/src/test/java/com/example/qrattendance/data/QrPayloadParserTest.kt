package com.example.qrattendance.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Test

class QrPayloadParserTest {
  @Test
  fun parse_validAppScheme_returnsCheckInRequest() {
    val request = QrPayloadParser.parse("qr-attendance://checkin?sessionId=42&token=abc")

    assertEquals(42L, request?.sessionId)
    assertEquals("abc", request?.token)
  }

  @Test
  fun parse_missingRequiredFields_returnsNull() {
    assertNull(QrPayloadParser.parse("qr-attendance://checkin?sessionId=42"))
    assertNull(QrPayloadParser.parse("qr-attendance://checkin?token=abc"))
    assertNull(QrPayloadParser.parse("qr-attendance://checkin?sessionId=abc&token=token"))
  }

  @Test
  fun parse_wrongSchemeOrHost_returnsNull() {
    assertNull(QrPayloadParser.parse("https://example.com?sessionId=42&token=abc"))
    assertNull(QrPayloadParser.parse("qr-attendance://other?sessionId=42&token=abc"))
  }

  @Test
  fun parse_urlEncodedToken_decodesValue() {
    val request = QrPayloadParser.parse("qr-attendance://checkin?sessionId=42&token=abc%2Bdef%3D%3D")

    assertEquals("abc+def==", request?.token)
  }
}
