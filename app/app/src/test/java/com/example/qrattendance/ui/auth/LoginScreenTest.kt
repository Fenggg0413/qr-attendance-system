package com.example.qrattendance.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.qrattendance.theme.MyApplicationTheme
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun happyPath_showsLoginFormAndClick() {
    var clicked = false
    compose.setContent { MyApplicationTheme { LoginScreen(LoginUiState(), {}, {}, { clicked = true }) } }

    compose.onNodeWithText("学生考勤").assertIsDisplayed()
    compose.onNodeWithText("登录").performClick()

    assertTrue(clicked)
  }

  @Test
  fun errorState_showsMessage() {
    compose.setContent { MyApplicationTheme { LoginScreen(LoginUiState(error = "登录失败"), {}, {}, {}) } }

    compose.onNodeWithText("登录失败").assertIsDisplayed()
  }
}
