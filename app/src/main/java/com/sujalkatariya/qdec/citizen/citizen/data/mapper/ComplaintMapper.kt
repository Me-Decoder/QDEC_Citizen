package com.sujalkatariya.qdec.citizen.citizen.data.mapper

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.gson.Gson
import com.sujalkatariya.qdec.citizen.complaints.ComplaintEntity

fun mapToComplaintEntity(doc: DocumentSnapshot): ComplaintEntity {

    Log.d("CITIZEN_MAPPER", "----- NEW DOC -----")
    Log.d("CITIZEN_MAPPER", "ID: ${doc.id}")
    Log.d("CITIZEN_MAPPER", "DATA: ${doc.data}")

    // 🔥 BASIC FIELDS
    val complaintId = doc.id
    val userId = doc.getString("userId") ?: ""
    val userEmail = doc.getString("userEmail") ?: ""
    val fraudType = doc.getString("fraudType") ?: "Unknown"
    val description = doc.getString("description") ?: ""
    val location = doc.getString("location") ?: "Unknown"
    val policeStation = doc.getString("policeStation") ?: "Unknown"
    val status = doc.getString("status") ?: "PENDING"

    // 🔥 NEW FIELD (IMPORTANT)
    val assignedOfficerName = doc.getString("assignedOfficerName")

    Log.d("CITIZEN_MAPPER", "Officer: $assignedOfficerName")

    // 🔥 EVIDENCE LIST → JSON convert
    val evidenceListRaw =
        doc.get("evidenceList") as? List<Map<String, Any>> ?: emptyList()

    val evidenceJson = Gson().toJson(evidenceListRaw)

    // 🔥 FRAUD DETAILS → JSON convert
    val fraudDetailsRaw =
        doc.get("fraudDetails") as? Map<String, Any> ?: emptyMap()

    val fraudDetailsJson = Gson().toJson(fraudDetailsRaw)

    return ComplaintEntity(
        complaintId = complaintId,
        userId = userId,
        userEmail = userEmail,
        fraudType = fraudType,
        description = description,
        location = location,
        policeStation = policeStation,
        evidenceList = evidenceJson,
        fraudDetails = fraudDetailsJson,
        status = status,
        assignedOfficerName = assignedOfficerName
    )
}