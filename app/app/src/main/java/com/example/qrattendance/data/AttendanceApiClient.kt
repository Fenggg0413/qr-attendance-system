package com.example.qrattendance.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AttendanceApiClient(private val baseUrl: String = "http://10.0.2.2:8080/api") {
  private val json = Json { ignoreUnknownKeys = true }

  suspend fun login(username: String, password: String): LoginResponse =
    post("/auth/login", mapOf("username" to username, "password" to password), null)

  suspend fun checkIn(token: String, request: CheckInRequest): CheckInResponse = post("/student/check-ins", request, token)

  suspend fun records(token: String): List<AttendanceRecord> = get("/student/attendance-records", token)

  suspend fun submitLeave(token: String, request: LeaveRequest): LeaveResponse = post("/student/leave-requests", request, token)

  private suspend inline fun <reified T> get(path: String, bearer: String): T =
    request(path, "GET", null, bearer).let { json.decodeFromString<T>(it) }

  private suspend inline fun <reified T, reified B> post(path: String, body: B, bearer: String?): T =
    request(path, "POST", json.encodeToString(body), bearer).let { json.decodeFromString<T>(it) }

  private suspend fun request(path: String, method: String, body: String?, bearer: String?): String =
    withContext(Dispatchers.IO) {
      val connection =
        (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
          requestMethod = method
          connectTimeout = 5000
          readTimeout = 5000
          setRequestProperty("Content-Type", "application/json")
          bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
          if (body != null) {
            doOutput = true
            outputStream.use { it.write(body.toByteArray()) }
          }
        }
      val code = connection.responseCode
      val stream = if (code in 200..299) connection.inputStream else connection.errorStream
      val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
      if (code !in 200..299) error(text.ifBlank { "HTTP $code" })
      text
    }
}
