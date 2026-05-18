package com.example.qrattendance.core

// 统一结果封装：Success 携带值、Failure 携带错误文案与可选异常，便于 ViewModel 在网络/IO 调用后分支处理。
sealed interface AppResult<out T> {
  data class Success<T>(val value: T) : AppResult<T>
  data class Failure(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}
