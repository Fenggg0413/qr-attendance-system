package com.example.qrattendance.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.qrattendance.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { StudentApp(StudentUiState(), MainScreenViewModel()) }
  }

  @Test
  fun loginForm_exists() {
    composeTestRule.onNodeWithText("学生考勤").assertExists()
    composeTestRule.onNodeWithText("账号").assertExists()
  }
}
