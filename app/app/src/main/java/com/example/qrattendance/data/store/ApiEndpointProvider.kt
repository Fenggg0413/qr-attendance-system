package com.example.qrattendance.data.store

// 服务端 baseUrl 提供者：单测/调试时可注入固定地址覆盖默认值。
interface ApiEndpointProvider {
  fun baseUrl(): String
  fun save(baseUrl: String)
}