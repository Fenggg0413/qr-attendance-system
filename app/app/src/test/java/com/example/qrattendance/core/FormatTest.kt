package com.example.qrattendance.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

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

  @Test
  fun greetingFollowsDeviceTime() {
    assertEquals("早上好 ☀️", Format.greeting(LocalTime.of(8, 0)))
    assertEquals("中午好 🍚", Format.greeting(LocalTime.of(12, 0)))
    assertEquals("下午好 🌤️", Format.greeting(LocalTime.of(15, 0)))
    assertEquals("晚上好 🌙", Format.greeting(LocalTime.of(21, 0)))
  }
}
