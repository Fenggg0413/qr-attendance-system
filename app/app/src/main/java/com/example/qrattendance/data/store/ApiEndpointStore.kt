package com.example.qrattendance.data.store

import android.content.Context

class ApiEndpointStore(context: Context) {
  private val prefs = context.getSharedPreferences("api-endpoint", Context.MODE_PRIVATE)

  fun baseUrl(): String = prefs.getString("baseUrl", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

  fun save(baseUrl: String) {
    prefs.edit().putString("baseUrl", baseUrl.trim().ifBlank { DEFAULT_BASE_URL }).apply()
  }

  companion object {
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
  }
}
