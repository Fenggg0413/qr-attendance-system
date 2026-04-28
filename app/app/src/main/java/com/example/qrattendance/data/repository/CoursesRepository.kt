package com.example.qrattendance.data.repository

import com.example.qrattendance.data.AttendanceApiClient
import com.example.qrattendance.data.CourseSummary
import com.example.qrattendance.data.SessionStore

interface CoursesRepository {
  suspend fun courses(): List<CourseSummary>
}

class RemoteCoursesRepository(private val api: AttendanceApiClient, private val sessionStore: SessionStore) : CoursesRepository {
  override suspend fun courses(): List<CourseSummary> = api.courses(requireToken())

  private fun requireToken(): String = sessionStore.sessions.value?.token ?: error("未登录")
}
