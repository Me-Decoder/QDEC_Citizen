package com.sujalkatariya.qdec2.citizen.util

import android.content.Context
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback

object CloudinaryManager {

    fun init(context: Context) {

        val config = mapOf(
            "cloud_name" to "dujynv07g",
            "api_key" to "114232299216399",
            "api_secret" to "ol3QNXpgWtEi4O8HxM6PyiF1E_g"
        )

        try {
            MediaManager.init(context, config)
        } catch (e: Exception) {
            // already initialized
        }
    }

    fun uploadFile(
        context: Context,
        filePath: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        MediaManager.get().upload(filePath)
            .option("resource_type", "auto") // image/audio/video auto detect
            .callback(object : UploadCallback {

                override fun onStart(requestId: String?) {}

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {

                    val url = resultData?.get("secure_url").toString()
                    onSuccess(url)
                }

                override fun onError(requestId: String?, error: ErrorInfo?) {
                    onError(error?.description ?: "Upload failed")
                }

                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            })
            .dispatch()
    }
}