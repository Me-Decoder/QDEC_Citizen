package com.sujalkatariya.qdec.citizen.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sujalkatariya.qdec.citizen.DAO.AppDatabase
import com.sujalkatariya.qdec.citizen.citizen.data.mapper.mapToComplaintEntity
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

        Log.d("SYNC", "🔥 Worker STARTED")

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.complaintDao()

        val pendingList = dao.getPendingComplaints()
        val firestore = FirebaseFirestore.getInstance()
        val gson = Gson()

        Log.d("SYNC", "Pending size: ${pendingList.size}")

        if (pendingList.isEmpty()) {
            Log.d("SYNC", "No pending complaints")
            return Result.success()
        }

        return try {

            for (complaint in pendingList) {

                Log.d("SYNC", "Processing: ${complaint.complaintId}")

                val evidenceType =
                    object : TypeToken<List<EvidenceItem>>() {}.type

                val rawList: List<EvidenceItem> =
                    try {
                        gson.fromJson(complaint.evidenceList, evidenceType)
                    } catch (e: Exception) {
                        Log.e("SYNC", "Evidence JSON error")
                        emptyList()
                    }

                val fraudType =
                    object : TypeToken<HashMap<String, String>>() {}.type

                val fraudDetails: HashMap<String, String> =
                    try {
                        gson.fromJson(complaint.fraudDetails, fraudType)
                    } catch (e: Exception) {
                        Log.e("SYNC", "FraudDetails JSON error")
                        hashMapOf()
                    }

                val latLng = extractLatLng(complaint.location)

                val updatedEvidenceList = mutableListOf<Map<String, Any>>()

                // 🔥 SAFE EVIDENCE LOOP
                for (item in rawList) {

                    try {
                        val file = File(item.filePath)
                        if (!file.exists()) {
                            Log.e("SYNC", "File not found: ${item.filePath}")
                            continue
                        }

                        val tempFile = EvidenceEncryptionManager
                            .decryptToTempFile(applicationContext, item.filePath)

                        val url = try {
                            uploadSuspend(tempFile.absolutePath)
                        } catch (e: Exception) {
                            Log.e("SYNC", "Cloudinary fail: ${e.message}")
                            continue // skip this evidence
                        }

                        updatedEvidenceList.add(
                            mapOf(
                                "type" to item.type,
                                "fileUrl" to url,
                                "uploadedAt" to System.currentTimeMillis(),
                                "fileName" to tempFile.name
                            )
                        )

                    } catch (e: Exception) {
                        Log.e("SYNC", "Evidence error: ${e.message}")
                    }
                }

                val data = hashMapOf(

                    "complaintId" to complaint.complaintId,
                    "userId" to complaint.userId,
                    "userEmail" to complaint.userEmail,

                    "fraudType" to complaint.fraudType,
                    "description" to complaint.description,

                    "location" to complaint.location,
                    "geoPoint" to mapOf(
                        "lat" to latLng.first,
                        "lng" to latLng.second
                    ),

                    "policeStation" to complaint.policeStation,

                    "evidenceList" to updatedEvidenceList,
                    "fraudDetails" to fraudDetails,

                    "status" to "PENDING",
                    "statusHistory" to listOf(
                        mapOf(
                            "status" to "PENDING",
                            "time" to System.currentTimeMillis()
                        )
                    ),

                    "priority" to "MEDIUM",
                    "timestamp" to System.currentTimeMillis()
                )

                // 🔥 FIREBASE SAFE UPLOAD
                try {
                    firestore
                        .collection("complaints")
                        .document(complaint.complaintId)
                        .set(data)
                        .await()

                    Log.d("SYNC", "✅ Uploaded: ${complaint.complaintId}")

                    dao.updateStatus(complaint.complaintId, "SYNCED")

                } catch (e: Exception) {
                    Log.e("SYNC", "❌ Firebase fail: ${e.message}")
                    return Result.retry()
                }
            }

            // 🔥 FETCH UPDATE
            fetchFromFirebaseAndUpdateLocal(dao)

            Log.d("SYNC", "🔥 Worker SUCCESS")

            Result.success()

        } catch (e: Exception) {
            Log.e("SYNC", "❌ Worker crash: ${e.message}")
            return Result.retry()
        }
    }

    private suspend fun fetchFromFirebaseAndUpdateLocal(
        dao: com.sujalkatariya.qdec.citizen.DAO.ComplaintDao
    ) {

        try {
            val firestore = FirebaseFirestore.getInstance()

            val snapshot = firestore.collection("complaints")
                .get()
                .await()

            Log.d("SYNC", "Fetched: ${snapshot.size()}")

            for (doc in snapshot.documents) {

                val complaint = mapToComplaintEntity(doc)

                dao.insertOrUpdate(complaint)
            }

        } catch (e: Exception) {
            Log.e("SYNC", "Fetch error: ${e.message}")
        }
    }

    private suspend fun uploadSuspend(filePath: String): String {

        return suspendCancellableCoroutine { cont ->

            CloudinaryManager.uploadFile(
                applicationContext,
                filePath,
                onSuccess = { url -> cont.resume(url) },
                onError = { error ->
                    cont.resumeWithException(Exception(error))
                }
            )
        }
    }

    private fun extractLatLng(location: String): Pair<Double, Double> {
        return try {

            val lat = Regex("Lat: ([0-9.]+)")
                .find(location)?.groupValues?.get(1)?.toDouble() ?: 0.0

            val lng = Regex("Lng: ([0-9.]+)")
                .find(location)?.groupValues?.get(1)?.toDouble() ?: 0.0

            Pair(lat, lng)

        } catch (e: Exception) {
            Pair(0.0, 0.0)
        }
    }
}