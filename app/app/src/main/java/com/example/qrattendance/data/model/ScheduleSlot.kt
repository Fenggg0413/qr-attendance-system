package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

// 课表中一节课的展开形式：包含周几（weekday）、节次（period）、所属课程、教师、教室与学期。
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
