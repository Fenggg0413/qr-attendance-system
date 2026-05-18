package com.example.qrattendance.data.api

// 网络层统一异常：携带可选 statusCode，便于 UI 区分认证失败 / 业务错误 / 网络异常等情况。
class ApiException(message: String, val statusCode: Int? = null) : RuntimeException(message)
