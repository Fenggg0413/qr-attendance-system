package com.example.qrattendance.data.api

import com.example.qrattendance.data.model.AttendanceRecord
import com.example.qrattendance.data.model.Dashboard
import com.example.qrattendance.data.model.LeaveRequest
import com.example.qrattendance.data.model.LoginResponse
import com.example.qrattendance.data.model.ScheduleSlot
import com.example.qrattendance.data.model.TodaySession
import com.example.qrattendance.data.model.User

interface StudentApi {
  suspend fun login(username: String, password: String): LoginResponse
  suspend fun me(): User
  suspend fun dashboard(): Dashboard
  suspend fun schedule(): List<ScheduleSlot>
  suspend fun sessions(scope: String = "active"): List<TodaySession>
  suspend fun records(filter: String? = null): List<AttendanceRecord>
  suspend fun checkIn(sessionId: Long, token: String)
  suspend fun leaveRequests(): List<LeaveRequest>
  suspend fun submitLeave(sessionId: Long, reason: String): LeaveRequest
  suspend fun updateProfile(displayName: String): User
  suspend fun changePassword(currentPassword: String, newPassword: String)
}
