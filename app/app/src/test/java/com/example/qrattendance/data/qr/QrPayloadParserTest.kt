package com.example.qrattendance.data.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrPayloadParserTest {
  @Test
  fun parsesCheckInPayload() {
    val payload = QrPayloadParser.parse("qr-attendance://checkin?sessionId=42&token=abc")
    assertEquals(42L, payload?.sessionId)
    assertEquals("abc", payload?.token)
  }

  @Test
  fun rejectsUnexpectedPayload() {
    assertNull(QrPayloadParser.parse("https://example.com/checkin?sessionId=42&token=abc"))
  }
}
