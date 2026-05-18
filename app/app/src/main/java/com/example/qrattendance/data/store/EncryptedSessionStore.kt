package com.example.qrattendance.data.store

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.qrattendance.data.model.SessionSnapshot
import com.example.qrattendance.data.model.User

// AES256 加密的会话存储：JWT 落盘前后都不暴露明文，防止其他 App 通过 root 或备份读取 token。
class EncryptedSessionStore(context: Context) : SessionStore {
  // MasterKey 用 AES256_GCM；Key 加密用 AES256_SIV（确定性，便于按 key 查找），Value 加密用 AES256_GCM（随机化更安全）。
  private val prefs = EncryptedSharedPreferences.create(
    context,
    "student-session",
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
  )

  // 把登录返回的关键字段加密落盘。
  override fun save(token: String, user: User) {
    prefs.edit()
      .putString("token", token)
      .putString("username", user.username)
      // displayName 可能为空（管理员未填昵称），降级使用 name 字段，避免 UI 顶栏显示空白。
      .putString("displayName", user.displayName.ifBlank { user.name })
      .apply()
  }

  override fun token(): String? = prefs.getString("token", null)

  // 组装会话快照供 UI 顶栏/侧栏渲染；token 缺失视为未登录。
  override fun current(): SessionSnapshot? {
    val token = token() ?: return null
    return SessionSnapshot(
      token = token,
      username = prefs.getString("username", "").orEmpty(),
      displayName = prefs.getString("displayName", "").orEmpty(),
    )
  }

  // 登出或检测到会话过期时调用，一次清空全部加密字段。
  override fun clear() {
    prefs.edit().clear().apply()
  }
}
