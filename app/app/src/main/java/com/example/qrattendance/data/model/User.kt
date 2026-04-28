package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
  val id: Long = 0,
  val username: String = "",
  val role: String = "",
  val displayName: String = "",
  val name: String = "",
  val studentNo: String = "",
  val grade: String = "",
  val department: String = "",
)

@Serializable
data class LoginResponse(val token: String, val user: User)

data class SessionSnapshot(val token: String, val username: String, val displayName: String)
