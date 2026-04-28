package com.example.qrattendance.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AttendanceApiClientTest {
  private lateinit var server: MockWebServer

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun login_decodesLoginResponse() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"jwt-token","user":{"id":5,"username":"student1","role":"STUDENT","displayName":"李同学"}}"""))
    val client = AttendanceApiClient(baseUrl = server.url("/api").toString().trimEnd('/'))

    val response = client.login("student1", "student123")

    val request = server.takeRequest()
    assertEquals("/api/auth/login", request.path)
    assertTrue(request.body.readUtf8().contains("student1"))
    assertEquals("jwt-token", response.token)
    assertEquals("李同学", response.user.displayName)
  }

  @Test
  fun request_usesLatestBaseUrlFromProvider() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"jwt-token","user":{"username":"student1","role":"STUDENT","displayName":"李同学"}}"""))
    val firstBaseUrl = "http://127.0.0.1:1/api"
    val currentBaseUrl = server.url("/api").toString().trimEnd('/')
    val client = AttendanceApiClient(baseUrlProvider = { currentBaseUrl }, baseUrl = firstBaseUrl)

    val response = client.login("student1", "student123")

    assertEquals("/api/auth/login", server.takeRequest().path)
    assertEquals("jwt-token", response.token)
  }

  @Test
  fun courses_sendsBearerAndDecodesResponse() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"id":7,"name":"移动开发","code":"MOB","teacherName":"张老师","term":"2026 春"}]"""))
    val client = AttendanceApiClient(baseUrl = server.url("/api").toString().trimEnd('/'))

    val courses = client.courses("token-123")

    val request = server.takeRequest()
    assertEquals("/api/student/courses", request.path)
    assertEquals("Bearer token-123", request.headers["Authorization"])
    assertEquals(7L, courses.single().id)
    assertEquals("张老师", courses.single().teacherName)
  }

  @Test
  fun updateProfile_sendsJsonBody() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"displayName":"新名字"}"""))
    val client = AttendanceApiClient(baseUrl = server.url("/api").toString().trimEnd('/'))

    val profile = client.updateProfile("token-123", UpdateProfileRequest("新名字"))

    val request = server.takeRequest()
    assertEquals("PUT", request.method)
    assertEquals("/api/student/profile", request.path)
    assertTrue(request.body.readUtf8().contains("新名字"))
    assertEquals("新名字", profile.displayName)
  }

  @Test
  fun apiError_throwsApiExceptionWithMessage() = runTest {
    server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"Forbidden"}"""))
    val client = AttendanceApiClient(baseUrl = server.url("/api").toString().trimEnd('/'))

    try {
      client.sessions("token-123", "active")
      throw AssertionError("Expected ApiException")
    } catch (error: ApiException) {
      assertEquals(403, error.status)
      assertEquals("Forbidden", error.apiMessage)
    }
  }

  @Test
  fun unauthorized_throwsApiExceptionAndInvokesCallback() = runTest {
    server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))
    var unauthorized = false
    val client = AttendanceApiClient(baseUrl = server.url("/api").toString().trimEnd('/'), onUnauthorized = { unauthorized = true })

    try {
      client.records("token-123")
      throw AssertionError("Expected ApiException")
    } catch (error: ApiException) {
      assertEquals(401, error.status)
      assertEquals("Unauthorized", error.apiMessage)
      assertTrue(unauthorized)
    }
  }
}
