package com.example.qrattendance.ui.records

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.qrattendance.data.AttendanceRecord
import com.example.qrattendance.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordsScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun happyPath_showsRecord() {
    val state = RecordsUiState(records = listOf(AttendanceRecord(id = 1, courseName = "移动开发", status = "PRESENT")))
    compose.setContent { MyApplicationTheme { RecordsScreen(state, {}, {}) } }

    compose.onNodeWithText("移动开发").assertIsDisplayed()
  }

  @Test
  fun errorState_showsMessage() {
    compose.setContent { MyApplicationTheme { RecordsScreen(RecordsUiState(error = "记录加载失败"), {}, {}) } }

    compose.onNodeWithText("记录加载失败").assertIsDisplayed()
  }
}
