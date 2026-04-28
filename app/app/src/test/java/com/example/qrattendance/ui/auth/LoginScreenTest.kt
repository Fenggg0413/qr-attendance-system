package com.example.qrattendance.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.qrattendance.theme.MyApplicationTheme
import junit.framework.TestCase.assertEquals
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
    compose.onNodeWithText("服务器地址").assertIsDisplayed()
    compose.onNodeWithText("http://10.0.2.2:8080/api").assertIsDisplayed()
    compose.onNodeWithText("请输入账号").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("请输入密码").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("如忘记账号请联系教务").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("登录").performClick()

    assertTrue(clicked)
  }

  @Test
  fun errorState_showsMessage() {
    compose.setContent { MyApplicationTheme { LoginScreen(LoginUiState(error = "登录失败"), {}, {}, {}) } }

    compose.onNodeWithText("登录失败").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun serverUrlField_updatesValue() {
    var serverUrl by mutableStateOf("http://10.0.2.2:8080/api")
    compose.setContent {
      MyApplicationTheme {
        LoginScreen(
          state = LoginUiState(serverUrl = serverUrl),
          onUsernameChange = {},
          onPasswordChange = {},
          onServerUrlChange = { serverUrl = it },
          onLogin = {},
        )
      }
    }

    compose.onNodeWithTag("serverUrlField").performTextClearance()
    compose.onNodeWithTag("serverUrlField").performTextInput("http://192.168.1.23:8080/api")

    assertEquals("http://192.168.1.23:8080/api", serverUrl)
  }
}
