package com.example.qrattendance.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

interface ApiEndpointStore {
  val baseUrl: StateFlow<String>

  fun save(value: String): Result<String>

  companion object {
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/api"

    fun normalize(value: String): Result<String> =
      runCatching {
        val trimmed = value.trim().trimEnd('/')
        require(trimmed.isNotBlank())
        require(!trimmed.any { it.isWhitespace() })

        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid API endpoint")
        require(url.scheme == "http" || url.scheme == "https")

        val currentPath = url.encodedPath.trimEnd('/')
        val apiPath =
          when {
            currentPath.isBlank() || currentPath == "/" -> "/api"
            currentPath.endsWith("/api") -> currentPath
            else -> "$currentPath/api"
          }

        url
          .newBuilder()
          .encodedPath(apiPath)
          .query(null)
          .fragment(null)
          .build()
          .toString()
          .trimEnd('/')
      }
  }
}

class InMemoryApiEndpointStore(initial: String = ApiEndpointStore.DEFAULT_BASE_URL) : ApiEndpointStore {
  override val baseUrl = MutableStateFlow(initial)

  override fun save(value: String): Result<String> {
    val normalized = ApiEndpointStore.normalize(value)
    normalized.onSuccess { baseUrl.value = it }
    return normalized
  }
}

class SharedPreferencesApiEndpointStore(context: Context) : ApiEndpointStore {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val state = MutableStateFlow(prefs.getString(KEY_BASE_URL, null) ?: ApiEndpointStore.DEFAULT_BASE_URL)
  override val baseUrl: StateFlow<String> = state

  override fun save(value: String): Result<String> {
    val normalized = ApiEndpointStore.normalize(value)
    normalized.onSuccess { baseUrl ->
      prefs.edit().putString(KEY_BASE_URL, baseUrl).apply()
      state.value = baseUrl
    }
    return normalized
  }

  private companion object {
    const val PREFS_NAME = "qr_attendance_api_endpoint"
    const val KEY_BASE_URL = "base_url"
  }
}
