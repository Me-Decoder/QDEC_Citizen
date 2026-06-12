package com.sujalkatariya.qdec2.citizen.complaints


data class ComplaintModel(
    val complaintId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val fraudType: String = "",
    val description: String = "",
    val location: String = "",
    val policeStation: String = "",
    val evidenceList: List<String> = emptyList(),
    val status: String = "PENDING", // 🔥 important
    val timestamp: Long = System.currentTimeMillis()
)