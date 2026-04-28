package com.example.qrattendance.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.qrattendance.data.UserProfile
import com.example.qrattendance.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun happyPath_showsProfileActions() {
    val state = ProfileUiState(user = UserProfile(username = "student1", displayName = "李同学"), displayName = "李同学")
    compose.setContent { MyApplicationTheme { ProfileScreen(state, {}, {}, {}, {}, {}, {}) } }

    compose.onNodeWithText("编辑资料").assertIsDisplayed()
    compose.onNodeWithText("修改密码").assertIsDisplayed()
  }

  @Test
  fun errorState_showsMessage() {
    compose.setContent { MyApplicationTheme { ProfileScreen(ProfileUiState(error = "资料更新失败"), {}, {}, {}, {}, {}, {}) } }

    compose.onAllNodesWithText("资料更新失败").assertCountEquals(1)
  }
}
