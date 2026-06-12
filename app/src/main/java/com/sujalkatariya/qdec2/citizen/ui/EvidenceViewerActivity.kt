package com.sujalkatariya.qdec2.citizen.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.sujalkatariya.qdec2.citizen.util.EvidenceEncryptionManager
import java.io.File

class EvidenceViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra("path") ?: return
        val type = intent.getStringExtra("type") ?: return

        when (type) {

            // ---------------- IMAGE ----------------
            "IMAGE" -> {

                val extension = path.substringAfterLast(".", "jpg")
                val file = File(cacheDir, "temp_img.$extension")

                // 🔥 STREAM DECRYPT
                EvidenceEncryptionManager.decryptToFile(path, file)

                val bytes = file.readBytes()

                val img = ImageView(this)

                val bitmap =
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                img.setImageBitmap(bitmap)

                setContentView(img)
            }

            // ---------------- DOCUMENT ----------------
            "DOCUMENT" -> {

                val extension = path.substringAfterLast(".", "pdf")
                val file = File(cacheDir, "temp_doc.$extension")

                EvidenceEncryptionManager.decryptToFile(path, file)

                openExternal(file.readBytes(), path)
            }

            "AUDIO" -> {

                val extension = path.substringAfterLast(".", "m4a")
                val file = File(cacheDir, "temp_audio.$extension")

                EvidenceEncryptionManager.decryptToFile(path, file)

                openExternal(file.readBytes(), path)
            }
        }
    }

    // ---------------- EXTERNAL OPEN ----------------

    private fun openExternal(bytes: ByteArray, originalPath: String) {

        try {

            val cleanPath = originalPath.removeSuffix(".enc")
            val extension = cleanPath.substringAfterLast(".", "dat")

            val file = File(cacheDir, "temp_file.$extension")
            file.writeBytes(bytes)

            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file
            )

            // 🔥 DIFFERENT HANDLING
            val mimeType = when (extension.lowercase()) {

                // 🎵 AUDIO → strict
                "mp3", "m4a", "wav" -> "audio/*"

                // 🖼 IMAGE → strict (safety)
                "jpg", "jpeg", "png" -> "image/*"

                // 📄 DOCUMENT → OPEN ANY APP
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addCategory(Intent.CATEGORY_DEFAULT)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Open with"))

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Error opening file",
                Toast.LENGTH_SHORT
            ).show()
        }

        finish()
    }

    // ---------------- MIME TYPE ----------------

    private fun getMimeType(ext: String): String {

        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext.lowercase())
            ?: "*/*"
    }
}