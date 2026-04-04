package com.sujalkatariya.qdec.citizen.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.SecretKeySpec

object EvidenceEncryptionManager {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"

    private val key =
        SecretKeySpec(
            "QDEC_SECURE_KEY1".toByteArray(),
            ALGORITHM
        )

    // --------------------------------------------
    // 🔐 FILE ENCRYPTION (STREAM BASED)
    // --------------------------------------------

    fun decryptToTempFile(context: Context, encryptedPath: String): File {

        val inputFile = File(encryptedPath)

        val tempFile = File.createTempFile(
            "qdec_tmp_",
            inputFile.name,
            context.cacheDir
        )

        decryptToFile(
            inputPath = encryptedPath,
            outputFile = tempFile
        )

        return tempFile
    }
    fun saveEncryptedFromUri(
        context: Context,
        uri: Uri,
        name: String
    ): String {

        val inputStream =
            context.contentResolver.openInputStream(uri)
                ?: throw Exception("File not found")

        val extension =
            context.contentResolver.getType(uri)
                ?.substringAfterLast("/") ?: "dat"

        val outFile =
            File(context.filesDir, "$name.$extension") // ❌ .enc remove

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val cipherOutputStream =
            CipherOutputStream(
                FileOutputStream(outFile),
                cipher
            )

        inputStream.copyTo(cipherOutputStream)

        inputStream.close()
        cipherOutputStream.close()

        return outFile.absolutePath
    }

    // --------------------------------------------
    // 🔐 FILE DECRYPTION (STREAM BASED)
    // --------------------------------------------

    fun decryptToFile(
        inputPath: String,
        outputFile: File
    ) {

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key)

        val inputStream = FileInputStream(inputPath)

        val cipherInputStream =
            CipherInputStream(inputStream, cipher)

        val outputStream = FileOutputStream(outputFile)

        cipherInputStream.copyTo(outputStream)

        cipherInputStream.close()
        inputStream.close()
        outputStream.close()
    }

    // --------------------------------------------
    // 🔐 TEXT ENCRYPTION
    // --------------------------------------------

    fun encryptText(data: String): String {

        return try {

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val encryptedBytes =
                cipher.doFinal(data.toByteArray())

            Base64.encodeToString(
                encryptedBytes,
                Base64.DEFAULT
            )

        } catch (e: Exception) {
            e.printStackTrace()
            data
        }
    }

    fun decryptText(encrypted: String): String {

        return try {

            val decoded =
                Base64.decode(encrypted, Base64.DEFAULT)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key)

            val decryptedBytes =
                cipher.doFinal(decoded)

            String(decryptedBytes)

        } catch (e: Exception) {
            e.printStackTrace()
            encrypted
        }
    }
}