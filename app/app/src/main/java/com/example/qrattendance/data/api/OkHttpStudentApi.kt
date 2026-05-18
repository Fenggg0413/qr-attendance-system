package com.example.qrattendance.data.api

import com.example.qrattendance.data.model.AttendanceRecord
import com.example.qrattendance.data.model.Dashboard
import com.example.qrattendance.data.model.LeaveRequest
import com.example.qrattendance.data.model.LoginResponse
import com.example.qrattendance.data.model.ScheduleSlot
import com.example.qrattendance.data.model.TodaySession
import com.example.qrattendance.data.model.User
import com.example.qrattendance.data.store.ApiEndpointProvider
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

// StudentApi 的 OkHttp + kotlinx.serialization 实现：所有接口都通过私有 request<T>() 复用 Bearer 注入与错误解析。
class OkHttpStudentApi(
  private val client: OkHttpClient,
  private val endpointStore: ApiEndpointProvider,
  private val sessionStore: SessionStore,
) : StudentApi {
  // ignoreUnknownKeys: 容忍服务端新增字段；explicitNulls = false: 不序列化 null 字段以减少包体。
  private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
  private val mediaType = "application/json; charset=utf-8".toMediaType()

  // 登录：调 /api/auth/login → 拿到 token + user → 立即写入 sessionStore，后续 request 才能注入 Bearer。
  override suspend fun login(username: String, password: String): LoginResponse {
    val response = request<LoginResponse>("POST", "/api/auth/login", buildJsonObject {
      put("username", username)
      put("password", password)
    })
    sessionStore.save(response.token, response.user)
    return response
  }

  // 拉取个人档案。
  override suspend fun me(): User = request("GET", "/api/student/profile")
  // 拉取首页仪表板。
  override suspend fun dashboard(): Dashboard = request("GET", "/api/student/dashboard")
  // 拉取课表。
  override suspend fun schedule(): List<ScheduleSlot> = request("GET", "/api/student/schedule")
  // 拉取签到会话列表，scope 透传给服务端筛选。
  override suspend fun sessions(scope: String): List<TodaySession> = request("GET", "/api/student/sessions?scope=$scope")
  // 拉取考勤记录列表（filter 暂未被服务端使用）。
  override suspend fun records(filter: String?): List<AttendanceRecord> = request("GET", "/api/student/attendance-records")

  // 扫码签到：上传 sessionId + QR token；不关心响应体，所以用 JsonObject 占位仅依赖错误码判定。
  override suspend fun checkIn(sessionId: Long, token: String) {
    request<JsonObject>("POST", "/api/student/check-ins", buildJsonObject {
      put("sessionId", sessionId)
      put("token", token)
    })
  }

  // 拉取请假申请列表。
  override suspend fun leaveRequests(): List<LeaveRequest> = request("GET", "/api/student/leave-requests")

  // 提交请假：服务端只返回 { id, status } 等部分字段，所以手动拆 JSON 再补齐本地传入的 sessionId/reason 构造完整对象。
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

  // 更新昵称，返回完整 User，调用方负责把 displayName 写回本地会话。
  override suspend fun updateProfile(displayName: String): User =
    request("PUT", "/api/student/profile", buildJsonObject { put("displayName", displayName) })

  // 修改密码：currentPassword 由服务端二次校验，成功后不返回内容。
  override suspend fun changePassword(currentPassword: String, newPassword: String) {
    request<JsonObject>("POST", "/api/student/password", buildJsonObject {
      put("currentPassword", currentPassword)
      put("newPassword", newPassword)
    })
  }

  // 通用请求方法：拼接 baseUrl → 注入 Bearer → 编码 JSON body → 发起调用 → 解析响应或抛 ApiException。
  // inline + reified T 让 json.decodeFromString<T> 在编译期知道目标类型，避免运行时反射。
  private suspend inline fun <reified T> request(method: String, path: String, body: JsonObject? = null): T =
    withContext(Dispatchers.IO) {
      // baseUrl 末尾可能含 "/"，trimEnd 后再拼 path（以 "/" 开头）避免出现 "//" 双斜杠。
      val url = endpointStore.baseUrl().trimEnd('/') + path
      val builder = Request.Builder().url(url)
      // 已登录才注入 Bearer；未登录场景（如 /auth/login 自身）跳过头部，由服务端按公开接口处理。
      sessionStore.token()?.let { builder.header("Authorization", "Bearer $it") }
      val requestBody = body?.let { json.encodeToString(it).toRequestBody(mediaType) }
      when (method) {
        "GET" -> builder.get()
        // POST/PUT 必须带 body，没有时用 "{}" 占位防止 OkHttp 抛 "method requires body"。
        "POST" -> builder.post(requestBody ?: "{}".toRequestBody(mediaType))
        "PUT" -> builder.put(requestBody ?: "{}".toRequestBody(mediaType))
      }
      client.newCall(builder.build()).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          // 服务端 ApiExceptionHandler 返回 { message, error }，尝试提取 message；失败则用 HTTP 状态码兜底。
          val message = runCatching { json.parseToJsonElement(text).let { it as JsonObject }["message"]?.jsonPrimitive?.content }.getOrNull()
          throw ApiException(message ?: "请求失败：${response.code}", response.code)
        }
        json.decodeFromString<T>(text)
      }
    }
}
