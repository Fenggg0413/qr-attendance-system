package com.example.qrattendance.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface SessionStore {
  val sessions: StateFlow<Session?>

  suspend fun save(session: Session)

  suspend fun clear()
}

class InMemorySessionStore(initial: Session? = null) : SessionStore {
  private val state = MutableStateFlow(initial)
  override val sessions: StateFlow<Session?> = state

  override suspend fun save(session: Session) {
    state.value = session
  }

  override suspend fun clear() {
    state.value = null
  }
}

class EncryptedSharedPreferencesSessionStore(context: Context) : SessionStore {
  private val json = Json { ignoreUnknownKeys = true }
  private val prefs =
    EncryptedSharedPreferences.create(
      context,
      "qr_attendance_session",
      MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  private val state = MutableStateFlow(readSession())
  override val sessions: StateFlow<Session?> = state

  override suspend fun save(session: Session) {
    prefs.edit().putString(KEY_SESSION, json.encodeToString(session)).apply()
    state.value = session
  }

  override suspend fun clear() {
    prefs.edit().remove(KEY_SESSION).apply()
    state.value = null
  }

  private fun readSession(): Session? =
    prefs.getString(KEY_SESSION, null)?.let { raw -> runCatching { json.decodeFromString<Session>(raw) }.getOrNull() }

  private companion object {
    const val KEY_SESSION = "session"
  }
}
