package com.example.qrattendance.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
  @Test
  fun percentFormatsRate() {
    assertEquals("94.2%", Format.percent(0.942))
  }

  @Test
  fun recordDateLabelsTodayAndYesterday() {
    assertEquals("今天", Format.recordDateLabel(Format.today()))
    assertEquals("昨天", Format.recordDateLabel(Format.today().minusDays(1)))
  }
}
