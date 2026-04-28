package com.example.qrattendance.data.api

class ApiException(message: String, val statusCode: Int? = null) : RuntimeException(message)
