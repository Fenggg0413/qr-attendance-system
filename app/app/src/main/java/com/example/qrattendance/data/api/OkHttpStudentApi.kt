package com.example.qrattendance.data.api

import com.example.qrattendance.data.model.AttendanceRecord
import com.example.qrattendance.data.model.Dashboard
import com.example.qrattendance.data.model.LeaveRequest
import com.example.qrattendance.data.model.LoginResponse
import com.example.qrattendance.data.model.ScheduleSlot
import com.example.qrattendance.data.model.TodaySession
import com.example.qrattendance.data.model.User
import com.example.qrattendance.data.store.ApiEndpointStore
import com.example.qrattendance.data.store.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpStudentApi(
  private val client: OkHttpClient,
  private val endpointStore: ApiEndpointStore,
  private val sessionStore: SessionStore,
) : StudentApi {
  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
  private val mediaType = "application/json; charset=utf-8".toMediaType()

  override suspend fun login(username: String, password: String): LoginResponse {
    val response = request<LoginResponse>("POST", "/api/auth/login", buildJsonObject {
      put("username", username)
      put("password", password)
    })
    sessionStore.save(response.token, response.user)
    return response
  }

  override suspend fun me(): User = request("GET", "/api/student/profile")
  override suspend fun dashboard(): Dashboard = request("GET", "/api/student/dashboard")
  override suspend fun schedule(): List<ScheduleSlot> = request("GET", "/api/student/schedule")
  override suspend fun sessions(scope: String): List<TodaySession> = request("GET", "/api/student/sessions?scope=$scope")
  override suspend fun records(filter: String?): List<AttendanceRecord> = request("GET", "/api/student/attendance-records")

  override suspend fun checkIn(sessionId: Long, token: String) {
    request<JsonObject>("POST", "/api/student/check-ins", buildJsonObject {
      put("sessionId", sessionId)
      put("token", token)
    })
  }

  override suspend fun leaveRequests(): List<LeaveRequest> = request("GET", "/api/student/leave-requests")

  override suspend fun submitLeave(sessionId: Long, reason: String): LeaveRequest {
    val created = request<JsonObject>("POST", "/api/student/leave-requests", buildJsonObject {
      put("sessionId", sessionId)
      put("reason", reason)
    })
    return LeaveRequest(
      id = created["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
      sessionId = sessionId,
      reason = reason,
      status = created["status"]?.jsonPrimitive?.content ?: "PENDING",
    )
  }

  override suspend fun updateProfile(displayName: String): User =
    request("PUT", "/api/student/profile", buildJsonObject { put("displayName", displayName) })

  override suspend fun changePassword(currentPassword: String, newPassword: String) {
    request<JsonObject>("POST", "/api/student/password", buildJsonObject {
      put("currentPassword", currentPassword)
      put("newPassword", newPassword)
    })
  }

  private suspend inline fun <reified T> request(method: String, path: String, body: JsonObject? = null): T =
    withContext(Dispatchers.IO) {
      val url = endpointStore.baseUrl().trimEnd('/') + path
      val builder = Request.Builder().url(url)
      sessionStore.token()?.let { builder.header("Authorization", "Bearer $it") }
      val requestBody = body?.let { json.encodeToString(it).toRequestBody(mediaType) }
      when (method) {
        "GET" -> builder.get()
        "POST" -> builder.post(requestBody ?: "{}".toRequestBody(mediaType))
        "PUT" -> builder.put(requestBody ?: "{}".toRequestBody(mediaType))
      }
      client.newCall(builder.build()).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          val message = runCatching { json.parseToJsonElement(text).let { it as JsonObject }["message"]?.jsonPrimitive?.content }.getOrNull()
          throw ApiException(message ?: "请求失败：${response.code}", response.code)
        }
        json.decodeFromString<T>(text)
      }
    }
}
