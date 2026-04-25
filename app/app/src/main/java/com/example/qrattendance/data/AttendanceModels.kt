package com.example.qrattendance.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(val token: String, val user: UserProfile)

@Serializable
data class UserProfile(val id: Long = 0, val username: String = "", val role: String = "", val displayName: String = "")

@Serializable
data class CheckInRequest(val sessionId: Long, val token: String)

@Serializable
data class CheckInResponse(val duplicate: Boolean = false, val record: AttendanceRecord = AttendanceRecord())

@Serializable
data class AttendanceRecord(
  val id: Long = 0,
  @SerialName("session_id") val sessionId: Long = 0,
  @SerialName("course_name") val courseName: String = "",
  val status: String = "",
  @SerialName("checked_in_at") val checkedInAt: String? = null,
  val source: String = "",
)

@Serializable
data class LeaveRequest(val sessionId: Long, val reason: String)

@Serializable
data class LeaveResponse(val id: Long = 0, val status: String = "")
