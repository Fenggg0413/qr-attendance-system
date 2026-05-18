package com.example.qrattendance.data.store

import com.example.qrattendance.data.model.SessionSnapshot
import com.example.qrattendance.data.model.User

// 学生会话存储抽象：单测中可用内存实现替换加密 SharedPreferences。
interface SessionStore {
  // 登录后写入 token 与用户基础信息（username、displayName 等）。
  fun save(token: String, user: User)
  // 读取当前 JWT，未登录返 null。
  fun token(): String?
  // 返回当前会话快照（不含敏感档案字段），null 表示未登录或会话失效。
  fun current(): SessionSnapshot?
  // 登出或会话失效时清空全部本地数据。
  fun clear()
}
