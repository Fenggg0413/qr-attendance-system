package com.example.qrattendance.data.repository

import com.example.qrattendance.data.AttendanceApiClient
import com.example.qrattendance.data.SessionStore
import com.example.qrattendance.data.SessionSummary

interface SessionsRepository {
  suspend fun sessions(scope: String = "active"): List<SessionSummary>
}

class RemoteSessionsRepository(private val api: AttendanceApiClient, private val sessionStore: SessionStore) : SessionsRepository {
  override suspend fun sessions(scope: String): List<SessionSummary> = api.sessions(requireToken(), scope)

  private fun requireToken(): String = sessionStore.sessions.value?.token ?: error("未登录")
}
