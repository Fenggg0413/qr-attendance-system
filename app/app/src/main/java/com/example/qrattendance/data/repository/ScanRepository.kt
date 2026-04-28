package com.example.qrattendance.data.repository

import com.example.qrattendance.data.AttendanceApiClient
import com.example.qrattendance.data.CheckInRequest
import com.example.qrattendance.data.CheckInResponse
import com.example.qrattendance.data.SessionStore

interface ScanRepository {
  suspend fun checkIn(request: CheckInRequest): CheckInResponse
}

class RemoteScanRepository(private val api: AttendanceApiClient, private val sessionStore: SessionStore) : ScanRepository {
  override suspend fun checkIn(request: CheckInRequest): CheckInResponse = api.checkIn(requireToken(), request)

  private fun requireToken(): String = sessionStore.sessions.value?.token ?: error("未登录")
}
