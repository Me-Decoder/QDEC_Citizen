package com.sujalkatariya.qdec2.citizen.complaints

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "complaints")
data class ComplaintEntity(

    @PrimaryKey
    val complaintId: String,

    val userId: String,
    val userEmail: String,
    val fraudType: String,
    val description: String,
    val location: String,
    val policeStation: String,
    val evidenceList: String, // JSON string
    val fraudDetails: String,
    val status: String,
    val assignedOfficerName: String? = null
)