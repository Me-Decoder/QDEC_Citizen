package com.sujalkatariya.qdec2.citizen.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sujalkatariya.qdec2.citizen.DAO.AppDatabase
import com.sujalkatariya.qdec2.citizen.citizen.data.mapper.mapToComplaintEntity
import com.sujalkatariya.qdec2.citizen.citizen.data.model.EvidenceItem
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
        CloudinaryManager.init(applicationContext)

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.complaintDao()

        val pendingList = dao.getPendingComplaints()
        val firestore = FirebaseFirestore.getInstance()
        val gson = Gson()

        Log.d("SYNC", "Pending size: ${pendingList.size}")

        if (pendingList.isEmpty()) return Result.success()

        return try {

            for (complaint in pendingList) {

                if (complaint.evidenceList.isEmpty() || complaint.evidenceList == "[]") {

                    Log.e("SYNC", "⛔ Skipping EMPTY evidence complaint: ${complaint.complaintId}")

                    continue

                }

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
                        hashMapOf()
                    }

                val latLng = extractLatLng(complaint.location)

                val updatedEvidenceList = mutableListOf<Map<String, Any>>()

                // 🔥 FIXED LOOP
                for (item in rawList) {

                    try {

                        Log.d("SYNC", "➡️ File: ${item.filePath}")

                        val originalFile = File(item.filePath)

                        if (!originalFile.exists()) {
                            Log.e("SYNC", "❌ File not found")
                            continue
                        }

                        // 🔥 STEP 1: DECRYPT
                        val tempFile = EvidenceEncryptionManager
                            .decryptToTempFile(applicationContext, item.filePath)

                        if (!tempFile.exists()) {
                            Log.e("SYNC", "❌ Temp file missing")
                            continue
                        }

                        // 🔥 STEP 2: CREATE CORRECT EXTENSION FILE
                        val fixedFile = when (item.type) {

                            "IMAGE" -> File(applicationContext.cacheDir, "${System.currentTimeMillis()}.jpg")

                            "AUDIO" -> File(applicationContext.cacheDir, "${System.currentTimeMillis()}.m4a")

                            "DOCUMENT" -> File(applicationContext.cacheDir, "${System.currentTimeMillis()}.pdf")

                            else -> File(applicationContext.cacheDir, "${System.currentTimeMillis()}")
                        }

                        // 🔥 STEP 3: COPY DATA
                        tempFile.inputStream().use { input ->
                            fixedFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        Log.d("SYNC", "Upload file: ${fixedFile.absolutePath}")

                        // 🔥 STEP 4: UPLOAD
                        val url = try {
                            uploadSuspend(fixedFile.absolutePath)
                        } catch (e: Exception) {
                            Log.e("SYNC", "❌ Upload fail: ${e.message}")
                            continue
                        }

                        Log.d("SYNC", "✅ URL: $url")

                        // 🔥 STEP 5: ADD TO LIST
                        updatedEvidenceList.add(
                            mapOf(
                                "type" to item.type,
                                "fileUrl" to url,
                                "uploadedAt" to System.currentTimeMillis()
                            )
                        )

                    } catch (e: Exception) {
                        Log.e("SYNC", "❌ Evidence error: ${e.message}")
                    }
                }

                Log.d("SYNC", "FINAL evidence size: ${updatedEvidenceList.size}")

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

                firestore
                    .collection("complaints")
                    .document(complaint.complaintId)
                    .set(data)
                    .await()

                Log.d("SYNC", "✅ Uploaded to Firebase")

                dao.updateStatus(complaint.complaintId, "SYNCED")
            }

            fetchFromFirebaseAndUpdateLocal(dao)

            Log.d("SYNC", "🔥 Worker SUCCESS")

            Result.success()

        } catch (e: Exception) {
            Log.e("SYNC", "❌ Worker crash: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun fetchFromFirebaseAndUpdateLocal(
        dao: com.sujalkatariya.qdec2.citizen.DAO.ComplaintDao
    ) {
        try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("complaints")
                .get()
                .await()

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