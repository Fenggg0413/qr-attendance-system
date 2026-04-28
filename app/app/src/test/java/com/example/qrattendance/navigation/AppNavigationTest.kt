package com.example.qrattendance.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.qrattendance.data.AttendanceRecord
import com.example.qrattendance.data.CheckInRequest
import com.example.qrattendance.data.CheckInResponse
import com.example.qrattendance.data.InMemorySessionStore
import com.example.qrattendance.data.LeaveRequestSummary
import com.example.qrattendance.data.LeaveResponse
import com.example.qrattendance.data.LoginResponse
import com.example.qrattendance.data.Session
import com.example.qrattendance.data.SessionSummary
import com.example.qrattendance.data.UserProfile
import com.example.qrattendance.data.repository.AuthRepository
import com.example.qrattendance.data.repository.LeaveRepository
import com.example.qrattendance.data.repository.ProfileRepository
import com.example.qrattendance.data.repository.RecordsRepository
import com.example.qrattendance.data.repository.ScanRepository
import com.example.qrattendance.data.repository.SessionsRepository
import com.example.qrattendance.theme.MyApplicationTheme
import com.example.qrattendance.data.AppContainer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppNavigationTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun emptySession_rendersLoginScreen() {
    compose.setContent { MyApplicationTheme { AppNavigation(testContainer(InMemorySessionStore())) } }

    compose.onNodeWithText("学生考勤").assertIsDisplayed()
    compose.onNodeWithText("请输入账号").assertIsDisplayed()
  }

  @Test
  fun loggedInSession_rendersHomeShellTabs() {
    val session = Session("token", UserProfile(username = "student1", role = "STUDENT", displayName = "李同学"))

    compose.setContent { MyApplicationTheme { AppNavigation(testContainer(InMemorySessionStore(session))) } }

    compose.onNodeWithText("首页").assertIsDisplayed()
    compose.onNodeWithText("会话").assertIsDisplayed()
    compose.onNodeWithText("记录").assertIsDisplayed()
    compose.onNodeWithText("我的").assertIsDisplayed()
  }
}

private fun testContainer(sessionStore: InMemorySessionStore): AppContainer =
  AppContainer(
    sessionStore = sessionStore,
    authRepository = FakeAuthRepository(),
    sessionsRepository = FakeSessionsRepository(),
    recordsRepository = FakeRecordsRepository(),
    leaveRepository = FakeLeaveRepository(),
    profileRepository = FakeProfileRepository(sessionStore),
    scanRepository = FakeScanRepository(),
  )

private class FakeAuthRepository : AuthRepository {
  override suspend fun login(username: String, password: String): LoginResponse = LoginResponse("token", UserProfile(username = username, role = "STUDENT", displayName = "同学"))

  override suspend fun logout() = Unit
}

private class FakeSessionsRepository : SessionsRepository {
  override suspend fun sessions(scope: String): List<SessionSummary> = emptyList()
}

private class FakeRecordsRepository : RecordsRepository {
  override suspend fun records(): List<AttendanceRecord> = emptyList()
}

private class FakeLeaveRepository : LeaveRepository {
  override suspend fun leaveRequests(): List<LeaveRequestSummary> = emptyList()

  override suspend fun submitLeave(sessionId: Long, reason: String): LeaveResponse = LeaveResponse(1, "PENDING")
}

private class FakeProfileRepository(private val sessionStore: InMemorySessionStore) : ProfileRepository {
  override val currentSession: Session?
    get() = sessionStore.sessions.value

  override suspend fun updateProfile(displayName: String): UserProfile = currentSession?.user?.copy(displayName = displayName) ?: UserProfile(displayName = displayName)

  override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit

  override suspend fun logout() {
    sessionStore.clear()
  }
}

private class FakeScanRepository : ScanRepository {
  override suspend fun checkIn(request: CheckInRequest): CheckInResponse = CheckInResponse()
}
