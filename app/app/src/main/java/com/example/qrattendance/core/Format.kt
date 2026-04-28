package com.example.qrattendance.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

object Format {
  private val monthDay = DateTimeFormatter.ofPattern("M月d日")

  fun percent(value: Double): String = String.format("%.1f%%", value * 100)

  fun today(): LocalDate = LocalDate.now()

  fun greeting(time: LocalTime = LocalTime.now()): String =
    when (time.hour) {
      in 5..10 -> "早上好 ☀️"
      in 11..13 -> "中午好 🍚"
      in 14..17 -> "下午好 🌤️"
      else -> "晚上好 🌙"
    }

  fun recordDateLabel(date: LocalDate): String =
    when (date) {
      today() -> "今天"
      today().minusDays(1) -> "昨天"
      else -> monthDay.format(date)
    }

  fun currentSemesterWeek(start: LocalDate = LocalDate.of(2026, 2, 23)): Int {
    val days = ChronoUnit.DAYS.between(start, today()).coerceAtLeast(0)
    return ceil((days + 1) / 7.0).toInt().coerceAtLeast(1)
  }

  fun compactDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return value.substring(0, minOf(value.length, 16)).replace('T', ' ')
  }
}
