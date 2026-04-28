package com.example.qrattendance.data

import app.cash.turbine.test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SessionStoreTest {
  @Test
  fun saveAndClear_updatesSessionFlow() = runTest {
    val store = InMemorySessionStore()

    store.sessions.test {
      assertNull(awaitItem())

      val session = Session("token", UserProfile(id = 9, username = "student", role = "STUDENT", displayName = "李同学"))
      store.save(session)
      assertEquals(session, awaitItem())

      store.clear()
      assertNull(awaitItem())
    }
  }
}
