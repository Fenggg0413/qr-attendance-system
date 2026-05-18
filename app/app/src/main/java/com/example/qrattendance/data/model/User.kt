package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

// 学生（或其他角色）档案：username 是登录账号，displayName 是昵称，studentNo 是学号。
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

// 登录接口响应：服务端签发的 JWT token + 当前用户档案。
@Serializable
data class LoginResponse(val token: String, val user: User)

// 落盘后的轻量会话快照：UI 顶栏渲染时使用，不持有完整 User 避免误用敏感字段。
data class SessionSnapshot(val token: String, val username: String, val displayName: String)
