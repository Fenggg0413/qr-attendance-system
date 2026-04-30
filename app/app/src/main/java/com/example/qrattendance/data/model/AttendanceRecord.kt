package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AttendanceRecord(
  val id: Long = 0,
  val sessionId: Long = 0,
  val courseName: String = "",
  val status: String = "",
  val checkedInAt: String? = null,
  val source: String = "",
  val teacherName: String = "",
  val classroomName: String = "",
)
