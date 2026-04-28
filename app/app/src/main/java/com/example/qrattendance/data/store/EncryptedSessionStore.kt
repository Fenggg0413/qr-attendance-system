package com.example.qrattendance.data.store

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.qrattendance.data.model.SessionSnapshot
import com.example.qrattendance.data.model.User

class EncryptedSessionStore(context: Context) : SessionStore {
  private val prefs = EncryptedSharedPreferences.create(
    context,
    "student-session",
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
  )

  override fun save(token: String, user: User) {
    prefs.edit()
      .putString("token", token)
      .putString("username", user.username)
      .putString("displayName", user.displayName.ifBlank { user.name })
      .apply()
  }

  override fun token(): String? = prefs.getString("token", null)

  override fun current(): SessionSnapshot? {
    val token = token() ?: return null
    return SessionSnapshot(
      token = token,
      username = prefs.getString("username", "").orEmpty(),
      displayName = prefs.getString("displayName", "").orEmpty(),
    )
  }

  override fun clear() {
    prefs.edit().clear().apply()
  }
}
