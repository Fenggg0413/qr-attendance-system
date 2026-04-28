package com.example.qrattendance.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.example.qrattendance.data.AttendanceRecord
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
    compose.onAllNodes(hasText("个待签到", substring = true), useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun emptyState_showsNoActiveSession() {
    compose.setContent { MyApplicationTheme { HomeScreen(HomeUiState(), {}, { _, _ -> }, {}) } }

    compose.onNodeWithText("暂无进行中会话").assertIsDisplayed()
    compose.onAllNodes(hasText("个待签到", substring = true), useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun recentRecords_showsRecentActivitySection() {
    val state = HomeUiState(recentRecords = listOf(AttendanceRecord(id = 1, courseName = "移动开发", status = "PRESENT")))

    compose.setContent { MyApplicationTheme { HomeScreen(state, {}, { _, _ -> }, {}) } }

    compose.onNode(hasScrollAction()).performScrollToNode(hasText("最近活动"))
    compose.onNodeWithText("最近活动").assertIsDisplayed()
    compose.onNodeWithText("移动开发").assertIsDisplayed()
  }
}
