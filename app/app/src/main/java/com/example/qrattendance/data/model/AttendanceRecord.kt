package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

// 单条考勤记录：对应服务端 attendance_records 表行；status 取值 PRESENT/LATE/ABSENT/EXCUSED。
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
