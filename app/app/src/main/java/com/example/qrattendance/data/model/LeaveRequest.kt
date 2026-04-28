package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LeaveRequest(
  val id: Long = 0,
  val sessionId: Long = 0,
  val courseName: String = "",
  val reason: String = "",
  val status: String = "",
  val createdAt: String = "",
  val reviewedAt: String? = null,
)
