package com.example.qrattendance.ui.schedule

// 单节课时间段：period 节次序号，start/end 是字符串形式的起止时间（如 "08:00"/"08:45"）。
data class SchedulePeriod(val period: Int, val start: String, val end: String)

// 学校固定作息表：与服务端 SchedulePeriods.java 保持一致，前后端各持一份避免每次都请求。
object SchedulePeriods {
  val items = listOf(
    SchedulePeriod(1, "08:00", "08:45"),
    SchedulePeriod(2, "08:50", "09:35"),
    SchedulePeriod(3, "09:50", "10:35"),
    SchedulePeriod(4, "10:40", "11:25"),
    SchedulePeriod(5, "11:30", "12:15"),
    SchedulePeriod(6, "13:45", "14:30"),
    SchedulePeriod(7, "14:35", "15:20"),
    SchedulePeriod(8, "15:35", "16:20"),
    SchedulePeriod(9, "16:25", "17:10"),
  )

  // 按节次查找对应时间段；未匹配时返回带 "-" 的兜底值，避免 UI 显示空白。
  fun byPeriod(period: Int): SchedulePeriod = items.firstOrNull { it.period == period } ?: SchedulePeriod(period, "-", "-")
}
