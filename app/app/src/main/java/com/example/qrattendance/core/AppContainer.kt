package com.example.qrattendance.core

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.qrattendance.data.api.OkHttpStudentApi
import com.example.qrattendance.data.api.StudentApi
import com.example.qrattendance.data.store.ApiEndpointStore
import com.example.qrattendance.data.store.EncryptedSessionStore
import com.example.qrattendance.data.store.SessionStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// 手动 DI 容器：App 启动时一次性装配所有核心依赖，通过 LocalContainer 暴露给 Compose 树使用。
class AppContainer(context: Context) {
  // 服务端 baseUrl 持久化：保存用户在设置页填写的地址（默认 10.0.2.2:8080 指向模拟器宿主）。
  val endpointStore = ApiEndpointStore(context)
  // 加密会话存储：登录后写入 token/username/displayName，使用 EncryptedSharedPreferences 保证落盘加密。
  val sessionStore: SessionStore = EncryptedSessionStore(context)
  // 全局共享一个 OkHttpClient 实例（复用连接池/线程池）；10s 连接/读取超时避免坏网络下 UI 卡死。
  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .build()
  // 学生端 API 实现：拼接 baseUrl、注入 Bearer Token、统一 JSON 编解码与错误处理。
  val api: StudentApi = OkHttpStudentApi(httpClient, endpointStore, sessionStore)
}

// Compose CompositionLocal：让任意 Composable 通过 LocalContainer.current 拿到容器，无需层层透传。
val LocalContainer = staticCompositionLocalOf<AppContainer> {
  error("AppContainer is not provided")
}
