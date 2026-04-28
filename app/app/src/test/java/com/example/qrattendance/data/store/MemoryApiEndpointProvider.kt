package com.example.qrattendance.data.store

class MemoryApiEndpointProvider(
  private var url: String = ApiEndpointStore.DEFAULT_BASE_URL,
) : ApiEndpointProvider {
  override fun baseUrl(): String = url

  override fun save(baseUrl: String) {
    url = baseUrl.trim().ifBlank { ApiEndpointStore.DEFAULT_BASE_URL }
  }
}