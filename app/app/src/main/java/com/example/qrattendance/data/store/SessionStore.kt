package com.example.qrattendance.data.store

import com.example.qrattendance.data.model.SessionSnapshot
import com.example.qrattendance.data.model.User

interface SessionStore {
  fun save(token: String, user: User)
  fun token(): String?
  fun current(): SessionSnapshot?
  fun clear()
}
