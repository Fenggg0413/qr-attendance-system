package com.example.qrattendance.data.api

import com.example.qrattendance.data.model.AttendanceRecord
import com.example.qrattendance.data.model.Dashboard
import com.example.qrattendance.data.model.LeaveRequest
import com.example.qrattendance.data.model.LoginResponse
import com.example.qrattendance.data.model.ScheduleSlot
import com.example.qrattendance.data.model.TodaySession
import com.example.qrattendance.data.model.User

class FakeStudentApi : StudentApi {
  override suspend fun login(username: String, password: String): LoginResponse =
    LoginResponse("token-$username", User(id = 1, username = username, role = "STUDENT", displayName = "李同学"))

  override suspend fun me(): User = User(username = "student1", displayName = "李同学", studentNo = "20230001", grade = "2023", department = "计算机学院")

  override suspend fun dashboard(): Dashboard =
    Dashboard(
      todayCount = 3,
      checkedInCount = 8,
      pendingLeaveCount = 1,
      absentCount = 2,
      lateCount = 1,
      semesterAttendanceRate = 0.9,
      todaySessions = listOf(TodaySession(id = 1, courseName = "移动开发", status = "OPEN")),
    )

  override suspend fun schedule(): List<ScheduleSlot> =
    listOf(
      ScheduleSlot(slotId = 1, weekday = "周一", period = 1, courseId = 1, courseName = "高等数学"),
      ScheduleSlot(slotId = 2, weekday = "周二", period = 2, courseId = 2, courseName = "移动开发"),
    )

  override suspend fun sessions(scope: String): List<TodaySession> =
    listOf(TodaySession(id = 1, courseName = "移动开发"), TodaySession(id = 2, courseName = "数据结构"))

  override suspend fun records(filter: String?): List<AttendanceRecord> =
    listOf(
      AttendanceRecord(id = 1, courseName = "移动开发", status = "PRESENT", checkedInAt = "2026-04-28T08:00:00Z"),
      AttendanceRecord(id = 2, courseName = "数据结构", status = "ABSENT", checkedInAt = "2026-04-27T08:00:00Z"),
      AttendanceRecord(id = 3, courseName = "英语", status = "LATE", checkedInAt = "2026-04-26T08:00:00Z"),
    )

  override suspend fun checkIn(sessionId: Long, token: String) = Unit

  override suspend fun leaveRequests(): List<LeaveRequest> =
    listOf(LeaveRequest(id = 1, sessionId = 1, courseName = "移动开发", reason = "病假", status = "PENDING"))

  override suspend fun submitLeave(sessionId: Long, reason: String): LeaveRequest =
    LeaveRequest(id = 2, sessionId = sessionId, reason = reason, status = "PENDING")

  override suspend fun updateProfile(displayName: String): User = User(username = "student1", displayName = displayName)

  override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit
}
