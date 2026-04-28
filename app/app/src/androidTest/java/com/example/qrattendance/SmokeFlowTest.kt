package com.example.qrattendance

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class SmokeFlowTest {
  @get:Rule
  val rule = createAndroidComposeRule<MainActivity>()

  @Test
  fun appLaunchesToLoginOrMain() {
    rule.onNodeWithText("使用学生账号登录").assertExists()
  }
}
