package com.example.qrattendance.data.repository

import com.example.qrattendance.data.AttendanceApiClient
import com.example.qrattendance.data.LeaveRequest
import com.example.qrattendance.data.LeaveRequestSummary
import com.example.qrattendance.data.LeaveResponse
import com.example.qrattendance.data.SessionStore

interface LeaveRepository {
  suspend fun leaveRequests(): List<LeaveRequestSummary>

  suspend fun submitLeave(sessionId: Long, reason: String): LeaveResponse
}

class RemoteLeaveRepository(private val api: AttendanceApiClient, private val sessionStore: SessionStore) : LeaveRepository {
  override suspend fun leaveRequests(): List<LeaveRequestSummary> = api.leaveRequests(requireToken())

  override suspend fun submitLeave(sessionId: Long, reason: String): LeaveResponse = api.submitLeave(requireToken(), LeaveRequest(sessionId, reason))

  private fun requireToken(): String = sessionStore.sessions.value?.token ?: error("未登录")
}
