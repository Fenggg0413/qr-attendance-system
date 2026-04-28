package com.example.qrattendance.ui.schedule

data class SchedulePeriod(val period: Int, val start: String, val end: String)

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

  fun byPeriod(period: Int): SchedulePeriod = items.firstOrNull { it.period == period } ?: SchedulePeriod(period, "-", "-")
}
