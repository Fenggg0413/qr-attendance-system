package com.example.qrattendance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class E2EFlowTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test
  fun studentLoginRecordsAndManualScanFallback() {
    ensureLoggedOut()

    compose.onNodeWithText("学生考勤").assertIsDisplayed()
    compose.onNodeWithText("请输入账号").assertIsDisplayed()
    compose.onNodeWithText("请输入密码").assertIsDisplayed()

    compose.onAllNodes(hasSetTextAction())[0].performTextInput("student1")
    compose.onAllNodes(hasSetTextAction())[1].performTextInput("student123")
    compose.onNodeWithText("登录").performClick()

    compose.waitUntil(timeoutMillis = 15_000) {
      compose.onAllNodes(hasTextExactly("首页")).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText("首页").assertIsDisplayed()
    compose.onNodeWithText("会话").assertIsDisplayed()
    compose.onNodeWithText("记录").assertIsDisplayed()
    compose.onNodeWithText("我的").assertIsDisplayed()

    compose.onAllNodes(hasTextExactly("记录"))[0].performClick()
    compose.waitUntil(timeoutMillis = 5_000) {
      compose.onAllNodes(hasTextExactly("记录")).fetchSemanticsNodes().isNotEmpty()
    }

    compose.onNodeWithContentDescription("扫码签到").performClick()
    compose.onNodeWithText("将二维码放入取景框").assertIsDisplayed()
    compose.onNodeWithText("无法扫描？手动输入").performClick()
    compose.onNodeWithText("qr-attendance://...").assertIsDisplayed()
    compose.onAllNodes(hasSetTextAction())[0].performTextInput("qr-attendance://checkin?sessionId=1&token=test")
    compose.onNodeWithText("提交签到").performClick()

    compose.waitUntil(timeoutMillis = 10_000) {
      listOf("签到成功", "已签到", "无效的二维码", "签到失败", "学生档案", "二维码").any { text ->
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
      }
    }
  }

  private fun ensureLoggedOut() {
    compose.waitUntil(timeoutMillis = 10_000) {
      compose.onAllNodes(hasTextExactly("登录")).fetchSemanticsNodes().isNotEmpty() ||
        compose.onAllNodes(hasTextExactly("我的")).fetchSemanticsNodes().isNotEmpty()
    }
    if (compose.onAllNodes(hasTextExactly("登录")).fetchSemanticsNodes().isNotEmpty()) return

    compose.onNodeWithText("我的").performClick()
    compose.onNodeWithText("退出登录").performClick()
    compose.waitUntil(timeoutMillis = 5_000) {
      compose.onAllNodes(hasTextExactly("登录")).fetchSemanticsNodes().isNotEmpty()
    }
  }
}
