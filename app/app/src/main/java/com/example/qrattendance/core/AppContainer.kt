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

class AppContainer(context: Context) {
  val endpointStore = ApiEndpointStore(context)
  val sessionStore: SessionStore = EncryptedSessionStore(context)
  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .build()
  val api: StudentApi = OkHttpStudentApi(httpClient, endpointStore, sessionStore)
}

val LocalContainer = staticCompositionLocalOf<AppContainer> {
  error("AppContainer is not provided")
}
