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

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.complaintDao()

        val pendingList = dao.getPendingComplaints()
        val firestore = FirebaseFirestore.getInstance()
        val gson = Gson()

        Log.d("SYNC", "Pending size: ${pendingList.size}")

        return try {

            // 🔥 STEP 1: Upload local pending complaints
            for (complaint in pendingList) {

                val evidenceType =
                    object : TypeToken<List<EvidenceItem>>() {}.type

                val rawList: List<EvidenceItem> =
                    gson.fromJson(complaint.evidenceList, evidenceType)

                val fraudType =
                    object : TypeToken<HashMap<String, String>>() {}.type

                val fraudDetails: HashMap<String, String> =
                    gson.fromJson(complaint.fraudDetails, fraudType)

                val latLng = extractLatLng(complaint.location)

                val updatedEvidenceList = mutableListOf<Map<String, Any>>()

                for (item in rawList) {

                    val file = File(item.filePath)
                    if (!file.exists()) continue

                    val tempFile = EvidenceEncryptionManager
                        .decryptToTempFile(applicationContext, item.filePath)

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

                dao.updateStatus(complaint.complaintId, "SYNCED")
            }

            // 🔥🔥 STEP 2: FETCH FROM FIREBASE → UPDATE LOCAL (MAIN FIX)
            fetchFromFirebaseAndUpdateLocal(dao)

            Result.success()

        } catch (e: Exception) {
            Log.e("SYNC", "Error: ${e.message}")
            e.printStackTrace()
            Result.retry()
        }
    }

    // 🔥 FETCH + LOCAL UPDATE
    private suspend fun fetchFromFirebaseAndUpdateLocal(
        dao: com.sujalkatariya.qdec.citizen.DAO.ComplaintDao
    ) {

        val firestore = FirebaseFirestore.getInstance()

        val snapshot = firestore.collection("complaints")
            .get()
            .await()

        Log.d("SYNC", "Fetching from Firebase: ${snapshot.size()}")

        for (doc in snapshot.documents) {

            val complaint = mapToComplaintEntity(doc)

            dao.insertOrUpdate(complaint)

            Log.d("SYNC", "Updated: ${complaint.complaintId} → ${complaint.assignedOfficerName}")
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