package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TodaySession(
  val id: Long = 0,
  val slotId: Long = 0,
  val period: Int = 0,
  val courseId: Long = 0,
  val courseName: String = "",
  val classroomName: String = "",
  val startedAt: String = "",
  val endsAt: String = "",
  val status: String = "",
  val method: String = "QR",
  val checkedIn: Boolean = false,
  val recordStatus: String = "",
  val hasLeave: Boolean = false,
  val canRequestLeave: Boolean = false,
)
