package com.example.qrattendance.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AttendanceApiClient(
  private val baseUrl: String = "http://10.0.2.2:8080/api",
  private val baseUrlProvider: () -> String = { baseUrl },
  private val client: OkHttpClient = OkHttpClient(),
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val onUnauthorized: suspend () -> Unit = {},
) {
  private val json = Json { ignoreUnknownKeys = true }
  private val mediaType = "application/json; charset=utf-8".toMediaType()

  suspend fun login(username: String, password: String): LoginResponse =
    post("/auth/login", mapOf("username" to username, "password" to password), null)

  suspend fun me(token: String): UserProfile = get("/me", token)

  suspend fun checkIn(token: String, request: CheckInRequest): CheckInResponse = post("/student/check-ins", request, token)

  suspend fun records(token: String): List<AttendanceRecord> = get("/student/attendance-records", token)

  suspend fun submitLeave(token: String, request: LeaveRequest): LeaveResponse = post("/student/leave-requests", request, token)

  suspend fun courses(token: String): List<CourseSummary> = get("/student/courses", token)

  suspend fun sessions(token: String, scope: String = "active"): List<SessionSummary> = get("/student/sessions?scope=$scope", token)

  suspend fun leaveRequests(token: String): List<LeaveRequestSummary> = get("/student/leave-requests", token)

  suspend fun updateProfile(token: String, request: UpdateProfileRequest): UserProfile = put("/student/profile", request, token)

  suspend fun changePassword(token: String, request: ChangePasswordRequest): Map<String, Boolean> = post("/student/password", request, token)

  private suspend inline fun <reified T> get(path: String, bearer: String): T =
    request(path, "GET", null, bearer).let { json.decodeFromString<T>(it) }

  private suspend inline fun <reified T, reified B> post(path: String, body: B, bearer: String?): T =
    request(path, "POST", json.encodeToString(body), bearer).let { json.decodeFromString<T>(it) }

  private suspend inline fun <reified T, reified B> put(path: String, body: B, bearer: String): T =
    request(path, "PUT", json.encodeToString(body), bearer).let { json.decodeFromString<T>(it) }

  private suspend fun request(path: String, method: String, body: String?, bearer: String?): String =
    withContext(dispatcher) {
      val requestBody = body?.toRequestBody(mediaType)
      val request =
        Request.Builder()
          .url("${baseUrlProvider()}$path")
          .method(method, requestBody)
          .header("Content-Type", "application/json")
          .also { builder -> bearer?.let { builder.header("Authorization", "Bearer $it") } }
          .build()
      client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          if (response.code == 401) onUnauthorized()
          val apiError = runCatching { json.decodeFromString<ApiError>(text) }.getOrNull()
          throw ApiException(response.code, apiError?.message ?: apiError?.error ?: text.ifBlank { response.message })
        }
        text
      }
    }
}
