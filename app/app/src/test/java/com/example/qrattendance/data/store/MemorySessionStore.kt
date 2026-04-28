package com.example.qrattendance.data.store

import com.example.qrattendance.data.model.SessionSnapshot
import com.example.qrattendance.data.model.User

class MemorySessionStore : SessionStore {
  private var snapshot: SessionSnapshot? = null

  override fun save(token: String, user: User) {
    snapshot = SessionSnapshot(token, user.username, user.displayName)
  }

  override fun token(): String? = snapshot?.token
  override fun current(): SessionSnapshot? = snapshot
  override fun clear() {
    snapshot = null
  }
}
