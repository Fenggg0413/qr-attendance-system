package com.example.qrattendance.data.repository

import com.example.qrattendance.data.AttendanceApiClient
import com.example.qrattendance.data.ChangePasswordRequest
import com.example.qrattendance.data.Session
import com.example.qrattendance.data.SessionStore
import com.example.qrattendance.data.UpdateProfileRequest
import com.example.qrattendance.data.UserProfile

interface ProfileRepository {
  val currentSession: Session?

  suspend fun updateProfile(displayName: String): UserProfile

  suspend fun changePassword(currentPassword: String, newPassword: String)

  suspend fun logout()
}

class RemoteProfileRepository(private val api: AttendanceApiClient, private val sessionStore: SessionStore) : ProfileRepository {
  override val currentSession: Session?
    get() = sessionStore.sessions.value

  override suspend fun updateProfile(displayName: String): UserProfile {
    val session = requireSession()
    val profile = api.updateProfile(session.token, UpdateProfileRequest(displayName))
    sessionStore.save(Session(session.token, session.user.copy(displayName = profile.displayName.ifBlank { displayName })))
    return profile
  }

  override suspend fun changePassword(currentPassword: String, newPassword: String) {
    api.changePassword(requireToken(), ChangePasswordRequest(currentPassword, newPassword))
  }

  override suspend fun logout() {
    sessionStore.clear()
  }

  private fun requireSession(): Session = sessionStore.sessions.value ?: error("未登录")

  private fun requireToken(): String = requireSession().token
}
