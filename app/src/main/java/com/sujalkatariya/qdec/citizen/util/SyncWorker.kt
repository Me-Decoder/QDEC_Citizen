package com.sujalkatariya.qdec.citizen.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sujalkatariya.qdec.citizen.DAO.AppDatabase
import com.sujalkatariya.qdec.citizen.citizen.data.model.EvidenceItem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.complaintDao()

        val pendingList = dao.getPendingComplaints()
        val firestore = FirebaseFirestore.getInstance()
        val gson = Gson()

        Log.d("SYNC", "Pending size: ${pendingList.size}")

        return try {

            for (complaint in pendingList) {

                // 🔥 JSON → List
                val evidenceType =
                    object : TypeToken<List<EvidenceItem>>() {}.type

                val rawList: List<EvidenceItem> =
                    gson.fromJson(complaint.evidenceList, evidenceType)

                // 🔥 JSON → Map
                val fraudType =
                    object : TypeToken<HashMap<String, String>>() {}.type

                val fraudDetails: HashMap<String, String> =
                    gson.fromJson(complaint.fraudDetails, fraudType)

                // 🔥 📍 EXTRACT LAT LNG
                val latLng = extractLatLng(complaint.location)

                // 🔥 ☁️ UPLOAD EVIDENCE
                val updatedEvidenceList = mutableListOf<Map<String, Any>>()

                for (item in rawList) {

                    val file = File(item.filePath)
                    if (!file.exists()) continue

                    // 🔐 decrypt file
                    val tempFile = EvidenceEncryptionManager
                        .decryptToTempFile(applicationContext, item.filePath)

                    // ☁️ upload
                    val url = uploadSuspend(tempFile.absolutePath)

                    updatedEvidenceList.add(
                        mapOf(
                            "type" to item.type,
                            "fileUrl" to url,
                            "uploadedAt" to System.currentTimeMillis(),
                            "fileName" to tempFile.name
                        )
                    )
                }

                // 🔥 FINAL FIRESTORE DATA
                val data = hashMapOf(

                    "complaintId" to complaint.complaintId,
                    "userId" to complaint.userId,
                    "userEmail" to complaint.userEmail,

                    "fraudType" to complaint.fraudType,
                    "description" to complaint.description,

                    // 📍 location
                    "location" to complaint.location,
                    "geoPoint" to mapOf(
                        "lat" to latLng.first,
                        "lng" to latLng.second
                    ),

                    "policeStation" to complaint.policeStation,

                    // 📁 evidence
                    "evidenceList" to updatedEvidenceList,

                    // 🔐 fraud details
                    "fraudDetails" to fraudDetails,

                    // 📊 status
                    "status" to "PENDING",
                    "statusHistory" to listOf(
                        mapOf(
                            "status" to "PENDING",
                            "time" to System.currentTimeMillis()
                        )
                    ),

                    // ⚡ optional smart field
                    "priority" to "MEDIUM",

                    "timestamp" to System.currentTimeMillis()
                )

                firestore
                    .collection("complaints")
                    .document(complaint.complaintId)
                    .set(data)
                    .await()

                // 🔥 mark synced
                dao.updateStatus(complaint.complaintId, "SYNCED")
            }

            Result.success()

        } catch (e: Exception) {
            Log.e("SYNC", "Error: ${e.message}")
            e.printStackTrace()
            Result.retry()
        }
    }

    // 🔥 CLOUDINARY UPLOAD
    private suspend fun uploadSuspend(filePath: String): String {

        return suspendCancellableCoroutine { cont ->

            CloudinaryManager.uploadFile(
                applicationContext,
                filePath,
                onSuccess = { url ->
                    cont.resume(url)
                },
                onError = { error ->
                    cont.resumeWithException(Exception(error))
                }
            )
        }
    }

    // 🔥 LAT LNG PARSER
    private fun extractLatLng(location: String): Pair<Double, Double> {
        return try {

            val lat = Regex("Lat: ([0-9.]+)")
                .find(location)
                ?.groupValues?.get(1)?.toDouble() ?: 0.0

            val lng = Regex("Lng: ([0-9.]+)")
                .find(location)
                ?.groupValues?.get(1)?.toDouble() ?: 0.0

            Pair(lat, lng)

        } catch (e: Exception) {
            Pair(0.0, 0.0)
        }
    }
}