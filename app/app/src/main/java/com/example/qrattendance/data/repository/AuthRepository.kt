package com.example.qrattendance.data.repository

import com.example.qrattendance.data.AttendanceApiClient
import com.example.qrattendance.data.LoginResponse
import com.example.qrattendance.data.Session
import com.example.qrattendance.data.SessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AuthRepository {
  suspend fun login(username: String, password: String): LoginResponse

  suspend fun logout()
}

class RemoteAuthRepository(
  private val api: AttendanceApiClient,
  private val sessionStore: SessionStore,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthRepository {
  override suspend fun login(username: String, password: String): LoginResponse =
    withContext(dispatcher) {
      val response = api.login(username, password)
      sessionStore.save(Session(response.token, response.user))
      response
    }

  override suspend fun logout() {
    sessionStore.clear()
  }
}
