package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

// 学生端首页仪表板：今日课程、出勤/请假计数、本学期出勤率，以及今日所有签到会话列表。
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
