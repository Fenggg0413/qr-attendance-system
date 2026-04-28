package com.example.qrattendance.ui.scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.qrattendance.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun manualFallback_showsInputSheet() {
    compose.setContent { MyApplicationTheme { ScanScreen(ScanUiState(), {}, {}, {}) } }

    compose.onNodeWithText("无法扫描？手动输入").assertIsDisplayed()
    compose.onNodeWithText("qr-attendance://...").assertDoesNotExist()

    compose.onNodeWithText("无法扫描？手动输入").performClick()

    compose.onNodeWithText("qr-attendance://...").assertIsDisplayed()
  }

  @Test
  fun loadingState_disablesSubmitButton() {
    compose.setContent { MyApplicationTheme { ScanScreen(ScanUiState(loading = true), {}, {}, {}) } }

    compose.onNodeWithText("无法扫描？手动输入").performClick()
    compose.onNodeWithText("提交中").assertIsNotEnabled()
  }

  @Test
  fun errorState_showsMessage() {
    compose.setContent { MyApplicationTheme { ScanScreen(ScanUiState(error = "二维码内容无效"), {}, {}, {}) } }

    compose.onAllNodesWithText("二维码内容无效").assertCountEquals(1)
  }
}
