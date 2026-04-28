package com.example.qrattendance.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.qrattendance.data.SessionSummary
import com.example.qrattendance.data.UserProfile
import com.example.qrattendance.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun happyPath_showsActiveSession() {
    val state = HomeUiState(user = UserProfile(displayName = "李同学"), activeSessions = listOf(SessionSummary(id = 1, courseName = "移动开发", status = "OPEN")))
    compose.setContent { MyApplicationTheme { HomeScreen(state, {}, { _, _ -> }, {}) } }

    compose.onNodeWithText("移动开发").assertIsDisplayed()
  }

  @Test
  fun emptyState_showsNoActiveSession() {
    compose.setContent { MyApplicationTheme { HomeScreen(HomeUiState(), {}, { _, _ -> }, {}) } }

    compose.onNodeWithText("暂无进行中会话").assertIsDisplayed()
  }
}
