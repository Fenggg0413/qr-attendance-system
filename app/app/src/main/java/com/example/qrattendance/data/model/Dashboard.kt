package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Dashboard(
  val todayCount: Int = 0,
  val checkedInCount: Int = 0,
  val pendingLeaveCount: Int = 0,
  val absentCount: Int = 0,
  val lateCount: Int = 0,
  val excusedCount: Int = 0,
  val semesterAttendanceRate: Double = 0.0,
  val todaySessions: List<TodaySession> = emptyList(),
)
