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
}
