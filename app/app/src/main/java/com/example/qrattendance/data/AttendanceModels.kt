package com.example.qrattendance.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(val token: String, val user: UserProfile)

@Serializable
data class UserProfile(val id: Long = 0, val username: String = "", val role: String = "", val displayName: String = "")

@Serializable
data class Session(val token: String, val user: UserProfile)

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

@Serializable
data class CourseSummary(
  val id: Long = 0,
  val name: String = "",
  val code: String = "",
  val teacherName: String = "",
  val term: String? = null,
)

@Serializable
data class SessionSummary(
  val id: Long = 0,
  val courseId: Long = 0,
  val courseName: String = "",
  val startedAt: String = "",
  val endsAt: String = "",
  val status: String = "",
  val method: String = "",
  val checkedIn: Boolean = false,
  val recordStatus: String = "",
  val hasLeave: Boolean = false,
  val canRequestLeave: Boolean = false,
)

@Serializable
data class LeaveRequestSummary(
  val id: Long = 0,
  val sessionId: Long = 0,
  val courseName: String = "",
  val reason: String = "",
  val status: String = "",
  val createdAt: String = "",
  val reviewedAt: String? = null,
)

@Serializable
data class UpdateProfileRequest(val displayName: String)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class ApiError(val message: String? = null, val error: String? = null)

class ApiException(val status: Int, val apiMessage: String) : Exception(apiMessage)
