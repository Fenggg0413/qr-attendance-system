package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleSlot(
  val slotId: Long = 0,
  val weekday: String = "",
  val period: Int = 0,
  val courseType: String = "",
  val courseId: Long = 0,
  val courseName: String = "",
  val courseCode: String = "",
  val classroomName: String = "",
  val classroomLocation: String = "",
  val teacherName: String = "",
  val term: String = "",
)
