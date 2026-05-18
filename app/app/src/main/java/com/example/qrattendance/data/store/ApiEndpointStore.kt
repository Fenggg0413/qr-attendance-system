package com.example.qrattendance.data.store

import android.content.Context

// 用普通 SharedPreferences 保存服务端 baseUrl，地址非敏感，无需加密。
class ApiEndpointStore(context: Context) : ApiEndpointProvider {
  private val prefs = context.getSharedPreferences("api-endpoint", Context.MODE_PRIVATE)

  // 读取保存的地址；未配置时降级到 DEFAULT_BASE_URL。
  override fun baseUrl(): String = prefs.getString("baseUrl", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

  // 写入新地址：trim 后空字符串再次降级到默认值，防止把用户清空的输入持久化。
  override fun save(baseUrl: String) {
    prefs.edit().putString("baseUrl", baseUrl.trim().ifBlank { DEFAULT_BASE_URL }).apply()
  }

  companion object {
    // 10.0.2.2 是 Android 模拟器访问宿主机 localhost 的特殊地址；真机调试需替换为局域网 IP。
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
  }
}
