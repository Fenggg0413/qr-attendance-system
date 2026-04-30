package com.example.qrattendance.data.api

import com.example.qrattendance.data.model.AttendanceRecord
import com.example.qrattendance.data.model.Dashboard
import com.example.qrattendance.data.model.LeaveRequest
import com.example.qrattendance.data.model.LoginResponse
import com.example.qrattendance.data.model.ScheduleSlot
import com.example.qrattendance.data.model.TodaySession
import com.example.qrattendance.data.model.User

class FakeStudentApi : StudentApi {
  var currentUser: User = User(username = "B22042101", displayName = "李同学", studentNo = "B22042101", grade = "2022", department = "计算机学院")
  var lastProfileDisplayName: String? = null
  var lastPasswordChange: Pair<String, String>? = null
  var updateProfileError: Throwable? = null
  var changePasswordError: Throwable? = null

  override suspend fun login(username: String, password: String): LoginResponse =
    LoginResponse("token-$username", User(id = 1, username = username, role = "STUDENT", displayName = "李同学"))

  override suspend fun me(): User = currentUser

  override suspend fun dashboard(): Dashboard =
    Dashboard(
      todayCount = 3,
      checkedInCount = 8,
      pendingLeaveCount = 1,
      absentCount = 2,
      lateCount = 0,
      semesterAttendanceRate = 0.9,
      todaySessions = listOf(TodaySession(id = 1, slotId = 1, period = 1, courseName = "移动开发", status = "OPEN")),
    )

  override suspend fun schedule(): List<ScheduleSlot> =
    listOf(
      ScheduleSlot(slotId = 1, weekday = "周一", period = 1, courseId = 1, courseName = "Java Web 开发"),
      ScheduleSlot(slotId = 2, weekday = "周一", period = 2, courseId = 1, courseName = "Java Web 开发"),
      ScheduleSlot(slotId = 3, weekday = "周三", period = 3, courseId = 1, courseName = "Java Web 开发"),
      ScheduleSlot(slotId = 4, weekday = "周三", period = 4, courseId = 1, courseName = "Java Web 开发"),
      ScheduleSlot(slotId = 5, weekday = "周五", period = 6, courseId = 1, courseName = "Java Web 开发"),
      ScheduleSlot(slotId = 6, weekday = "周五", period = 7, courseId = 1, courseName = "Java Web 开发"),
    )

  override suspend fun sessions(scope: String): List<TodaySession> =
    listOf(TodaySession(id = 1, courseName = "移动开发"), TodaySession(id = 2, courseName = "数据结构"))

  override suspend fun records(filter: String?): List<AttendanceRecord> =
    listOf(
      AttendanceRecord(id = 1, courseName = "移动开发", status = "PRESENT", checkedInAt = "2026-04-28T08:00:00Z", teacherName = "王老师", classroomName = "教1-301"),
      AttendanceRecord(id = 2, courseName = "数据结构", status = "ABSENT", checkedInAt = "2026-04-27T08:00:00Z", teacherName = "张老师", classroomName = "教2-205"),
      
    )

  override suspend fun checkIn(sessionId: Long, token: String) = Unit

  override suspend fun leaveRequests(): List<LeaveRequest> =
    listOf(LeaveRequest(id = 1, sessionId = 1, courseName = "移动开发", reason = "病假", status = "PENDING"))

  override suspend fun submitLeave(sessionId: Long, reason: String): LeaveRequest =
    LeaveRequest(id = 2, sessionId = sessionId, reason = reason, status = "PENDING")

  override suspend fun updateProfile(displayName: String): User {
    updateProfileError?.let { throw it }
    lastProfileDisplayName = displayName
    currentUser = currentUser.copy(displayName = displayName)
    return currentUser
  }

  override suspend fun changePassword(currentPassword: String, newPassword: String) {
    changePasswordError?.let { throw it }
    lastPasswordChange = currentPassword to newPassword
  }
}
