package com.example.qrattendance.data.store

interface ApiEndpointProvider {
  fun baseUrl(): String
  fun save(baseUrl: String)
}