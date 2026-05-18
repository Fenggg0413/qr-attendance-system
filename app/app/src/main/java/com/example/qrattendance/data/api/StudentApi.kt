package com.example.qrattendance.data.api

import com.example.qrattendance.data.model.AttendanceRecord
import com.example.qrattendance.data.model.Dashboard
import com.example.qrattendance.data.model.LeaveRequest
import com.example.qrattendance.data.model.LoginResponse
import com.example.qrattendance.data.model.ScheduleSlot
import com.example.qrattendance.data.model.TodaySession
import com.example.qrattendance.data.model.User

// 学生端 API 抽象：列出 App 用到的全部服务端接口，便于在测试中以 fake 实现替换。
interface StudentApi {
  // 用户名密码登录，成功后服务端返回 JWT token 与用户档案。
  suspend fun login(username: String, password: String): LoginResponse
  // 拉取当前登录学生的个人档案。
  suspend fun me(): User
  // 拉取学生首页仪表板数据（今日课程、出勤统计、待审请假等）。
  suspend fun dashboard(): Dashboard
  // 拉取学生本学期的课表（按周-节-课程展开的 slot 列表）。
  suspend fun schedule(): List<ScheduleSlot>
  // 拉取签到会话列表，scope 区分 active/today/history 等。
  suspend fun sessions(scope: String = "active"): List<TodaySession>
  // 拉取考勤记录；filter 预留按状态过滤，目前服务端忽略该参数。
  suspend fun records(filter: String? = null): List<AttendanceRecord>
  // 扫码签到：上传 sessionId 与 QR 短令牌，由服务端校验并写入考勤。
  suspend fun checkIn(sessionId: Long, token: String)
  // 拉取学生提交的请假申请列表。
  suspend fun leaveRequests(): List<LeaveRequest>
  // 针对某次签到会话提交请假申请，返回新建的请假对象。
  suspend fun submitLeave(sessionId: Long, reason: String): LeaveRequest
  // 修改昵称（displayName），服务端返回更新后的档案。
  suspend fun updateProfile(displayName: String): User
  // 修改登录密码，需提供当前密码做二次校验。
  suspend fun changePassword(currentPassword: String, newPassword: String)
}
