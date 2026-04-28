package com.example.qrattendance.data.repository

import com.example.qrattendance.data.AttendanceApiClient
import com.example.qrattendance.data.AttendanceRecord
import com.example.qrattendance.data.SessionStore

interface RecordsRepository {
  suspend fun records(): List<AttendanceRecord>
}

class RemoteRecordsRepository(private val api: AttendanceApiClient, private val sessionStore: SessionStore) : RecordsRepository {
  override suspend fun records(): List<AttendanceRecord> = api.records(requireToken())

  private fun requireToken(): String = sessionStore.sessions.value?.token ?: error("未登录")
}
