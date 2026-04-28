package com.example.qrattendance.ui.nav

import com.example.qrattendance.data.model.SessionSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavTest {
  @Test
  fun legacyStudent1SessionIsRejected() {
    assertTrue(shouldClearLegacySession(SessionSnapshot("token", "student1", "李同学")))
  }

  @Test
  fun realStudentSessionIsKept() {
    assertFalse(shouldClearLegacySession(SessionSnapshot("token", "B22042101", "李同学")))
  }
}
