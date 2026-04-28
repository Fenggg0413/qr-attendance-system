package com.example.qrattendance.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiEndpointStoreTest {
  private lateinit var store: SharedPreferencesApiEndpointStore

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("qr_attendance_api_endpoint", Context.MODE_PRIVATE).edit().clear().commit()
    store = SharedPreferencesApiEndpointStore(context)
  }

  @Test
  fun defaultBaseUrl_usesEmulatorHost() {
    assertEquals("http://10.0.2.2:8080/api", store.baseUrl.value)
  }

  @Test
  fun normalize_addsHttpSchemeAndApiPath() {
    val result = ApiEndpointStore.normalize("192.168.1.23:8080")

    assertTrue(result.isSuccess)
    assertEquals("http://192.168.1.23:8080/api", result.getOrThrow())
  }

  @Test
  fun normalize_preservesExistingApiPathAndTrimsTrailingSlash() {
    val result = ApiEndpointStore.normalize(" http://192.168.1.23:8080/api/ ")

    assertTrue(result.isSuccess)
    assertEquals("http://192.168.1.23:8080/api", result.getOrThrow())
  }

  @Test
  fun normalize_rejectsInvalidValue() {
    val result = ApiEndpointStore.normalize("not a host")

    assertFalse(result.isSuccess)
  }

  @Test
  fun save_persistsNormalizedBaseUrl() {
    val saved = store.save("192.168.1.23:8080")

    assertTrue(saved.isSuccess)
    assertEquals("http://192.168.1.23:8080/api", store.baseUrl.value)
    assertEquals("http://192.168.1.23:8080/api", SharedPreferencesApiEndpointStore(ApplicationProvider.getApplicationContext()).baseUrl.value)
  }
}
