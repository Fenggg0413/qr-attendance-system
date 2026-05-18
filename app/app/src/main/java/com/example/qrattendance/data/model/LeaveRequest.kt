package com.example.qrattendance.data.model

import kotlinx.serialization.Serializable

// 请假申请记录：status 取值 PENDING/APPROVED/REJECTED；reviewedAt 在审核后才有值。
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
