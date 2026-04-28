package com.example.qrattendance.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSecurityConfigTest {
  private val manifestPath = Path.of("src", "main", "AndroidManifest.xml")
  private val configPath = Path.of("src", "main", "res", "xml", "network_security_config.xml")
  private val debugConfigPath = Path.of("src", "debug", "res", "xml", "network_security_config.xml")

  @Test
  fun manifest_referencesNetworkSecurityConfig() {
    val manifest = manifestPath.readText()

    assertTrue(
      "Manifest should reference @xml/network_security_config",
      manifest.contains("""android:networkSecurityConfig="@xml/network_security_config""""),
    )
  }

  @Test
  fun networkSecurityConfig_allowsCleartextOnlyForEmulatorHost() {
    assertTrue("network_security_config.xml should exist", configPath.exists())

    val config = configPath.readText()

    assertTrue(config.contains("""cleartextTrafficPermitted="true""""))
    assertTrue(config.contains(">10.0.2.2<"))
    assertFalse(
      "Config should not enable cleartext globally",
      config.contains("<base-config cleartextTrafficPermitted=\"true\""),
    )
  }

  @Test
  fun debugNetworkSecurityConfig_allowsLocalDevelopmentCleartext() {
    assertTrue("debug network_security_config.xml should exist", debugConfigPath.exists())

    val config = debugConfigPath.readText()

    assertTrue(config.contains("<base-config cleartextTrafficPermitted=\"true\""))
  }
}
