package com.example.qrattendance.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

// 应用通用的轻量格式化工具：百分比、问候语、学期周次、时间字符串等。
object Format {
  private val monthDay = DateTimeFormatter.ofPattern("M月d日")

  // 把 0-1 的小数渲染为带 1 位小数的百分比字符串（0.876 -> "87.6%"）。
  fun percent(value: Double): String = String.format("%.1f%%", value * 100)

  // 取当前本地日期，独立包装方便单测中替换。
  fun today(): LocalDate = LocalDate.now()

  // 按小时段返回带 emoji 的中文问候语；默认参数取当前时间，便于单测注入固定时刻。
  fun greeting(time: LocalTime = LocalTime.now()): String =
    when (time.hour) {
      in 5..10 -> "早上好 ☀️"
      in 11..13 -> "中午好 🍚"
      in 14..17 -> "下午好 🌤️"
      else -> "晚上好 🌙"
    }

  // 把日期渲染为"今天/昨天/M月d日"等友好标签，用于考勤记录列表分组。
  fun recordDateLabel(date: LocalDate): String =
    when (date) {
      today() -> "今天"
      today().minusDays(1) -> "昨天"
      else -> monthDay.format(date)
    }

  // 计算当前是学期第几周（默认 2026-02-23 为第 1 周首日），用于课表 UI 高亮当前周。
  fun currentSemesterWeek(start: LocalDate = LocalDate.of(2026, 2, 23)): Int {
    val days = ChronoUnit.DAYS.between(start, today()).coerceAtLeast(0)
    return ceil((days + 1) / 7.0).toInt().coerceAtLeast(1)
  }

  // 把 ISO-8601 时间串截断到前 16 个字符并把 'T' 换为空格，null/空串显示 "-"。
  fun compactDateTime(value: String?): String {
    if (value.isNullOrBlank()) return "-"
    return value.substring(0, minOf(value.length, 16)).replace('T', ' ')
  }
}
