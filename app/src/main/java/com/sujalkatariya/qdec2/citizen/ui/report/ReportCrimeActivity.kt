package com.sujalkatariya.qdec2.citizen.ui.report

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.sujalkatariya.qdec2.citizen.R
import com.sujalkatariya.qdec2.citizen.databinding.ActivityReportCrimeBinding
import com.sujalkatariya.qdec2.citizen.DAO.AppDatabase
import com.sujalkatariya.qdec2.citizen.citizen.data.model.EvidenceItem
import com.sujalkatariya.qdec2.citizen.complaints.ComplaintEntity
import com.sujalkatariya.qdec2.citizen.ui.EvidenceViewerActivity
import com.sujalkatariya.qdec2.citizen.ui.adapter.EvidenceAdapter
import com.sujalkatariya.qdec2.citizen.ui.home.HomeActivity
import com.sujalkatariya.qdec2.citizen.util.EvidenceEncryptionManager
import com.sujalkatariya.qdec2.citizen.util.SyncWorker
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ReportCrimeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportCrimeBinding
    private val evidenceList = mutableListOf<EvidenceItem>()
    private lateinit var evidenceAdapter: EvidenceAdapter

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var pulseAnimator: ObjectAnimator? = null

    private val fraudTypes = listOf(
        "UPI Fraud",
        "Credit Card Fraud",
        "Social Media Scam",
        "Investment Scam",
        "Other"
    )

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    override fun onResume() {

        super.onResume()

        fetchLocation()

    }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                val file = File(cacheDir, "img_${System.currentTimeMillis()}.jpg")
                val stream = FileOutputStream(file)
                it.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.close()

                val path = EvidenceEncryptionManager.saveEncryptedFromUri(
                    this, Uri.fromFile(file), file.name
                )

                Log.d("FLOW", "Camera path: $path")

                if (!path.isNullOrEmpty()) {
                    evidenceList.add(EvidenceItem("IMAGE", path))
                    evidenceAdapter.notifyDataSetChanged()
                }
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val path = EvidenceEncryptionManager.saveEncryptedFromUri(
                    this, it, "img_${System.currentTimeMillis()}"
                )

                Log.d("FLOW", "Gallery path: $path")

                if (!path.isNullOrEmpty()) {
                    evidenceList.add(EvidenceItem("IMAGE", path))
                    evidenceAdapter.notifyDataSetChanged()
                }
            }
        }

    private val documentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val path = EvidenceEncryptionManager.saveEncryptedFromUri(
                    this, it, "doc_${System.currentTimeMillis()}"
                )

                Log.d("FLOW", "Document path: $path")

                if (!path.isNullOrEmpty()) {
                    evidenceList.add(EvidenceItem("DOCUMENT", path))
                    evidenceAdapter.notifyDataSetChanged()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReportCrimeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSubmit.isEnabled = false

        setupRecycler()
        setupDropdown()
        setupButtons()
        setupSubmit()
        checkLocationPermission()
    }

    private fun setupRecycler() {
        evidenceAdapter = EvidenceAdapter(evidenceList) {
            openEvidence(it)
        }
        binding.recyclerEvidence.layoutManager = LinearLayoutManager(this)
        binding.recyclerEvidence.adapter = evidenceAdapter
    }

    private fun openEvidence(item: EvidenceItem) {
        val intent = Intent(this, EvidenceViewerActivity::class.java)
        intent.putExtra("path", item.filePath)
        intent.putExtra("type", item.type)
        startActivity(intent)
    }

    private fun setupDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fraudTypes)
        binding.spFraudType.setAdapter(adapter)

        binding.spFraudType.setOnItemClickListener { _, _, position, _ ->
            binding.layoutFraudFields.removeAllViews()

            when (fraudTypes[position]) {
                "UPI Fraud" -> {
                    addField("Amount Lost")
                    addField("UPI ID")
                    addField("Transaction ID")
                }
                "Credit Card Fraud" -> {
                    addField("Card Last 4 Digits")
                    addField("Transaction Amount")
                }
                else -> addField("Details")
            }
        }
    }

    private fun addField(hint: String) {
        val layout = TextInputLayout(this)
        val edit = TextInputEditText(this)
        edit.hint = hint
        edit.tag = hint
        layout.addView(edit)
        binding.layoutFraudFields.addView(layout)
    }

    private fun setupButtons() {
        binding.btnCamera.setOnClickListener { cameraLauncher.launch(null) }
        binding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.btnDocument.setOnClickListener { documentLauncher.launch("*/*") }

        binding.btnAudio.setOnClickListener {
            if (!isRecording) startRecording() else stopRecording()
        }
    }
    private fun stopMicPulse() {

        pulseAnimator?.cancel()
        pulseAnimator = null

        binding.micPulse.alpha = 1f
        binding.micPulse.visibility = View.GONE
    }
    private fun startMicPulse() {

        // already running hoy to stop kari de (duplicate avoid)
        stopMicPulse()

        binding.micPulse.visibility = View.VISIBLE

        pulseAnimator = ObjectAnimator.ofFloat(
            binding.micPulse,
            "alpha",
            0.3f,
            1f
        ).apply {
            duration = 600L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun startRecording() {

        try {

            if (isRecording) return

            // 🔐 Permission check
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Mic permission required", Toast.LENGTH_SHORT).show()
                return
            }

            // 🔥 Release old instance (IMPORTANT)
            mediaRecorder?.release()
            mediaRecorder = null

            audioFile = File(cacheDir, "audio_${System.currentTimeMillis()}.m4a")

            mediaRecorder = MediaRecorder()

            mediaRecorder?.apply {

                // ⚠️ ORDER IMPORTANT
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)

                prepare()
                start()
            }

            isRecording = true
            startMicPulse()

            Log.d("AUDIO", "Recording started")

        } catch (e: Exception) {

            Log.e("AUDIO", "❌ Start failed: ${e.message}")
            e.printStackTrace()

            Toast.makeText(this, "Recording failed ❌", Toast.LENGTH_SHORT).show()

            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
        }
    }



    private fun stopRecording() {

        try {

            if (!isRecording) return

            stopMicPulse()

            mediaRecorder?.apply {
                stop()
                release()
            }

            mediaRecorder = null
            isRecording = false

            Log.d("AUDIO", "Recording stopped")

        } catch (e: Exception) {

            Log.e("AUDIO", "❌ Stop error: ${e.message}")
            e.printStackTrace()

            mediaRecorder = null
            isRecording = false
        }

        try {

            audioFile?.let {

                val path = EvidenceEncryptionManager.saveEncryptedFromUri(
                    this,
                    Uri.fromFile(it),
                    "audio_${System.currentTimeMillis()}"
                )

                Log.d("FLOW", "Audio path: $path")

                if (!path.isNullOrEmpty()) {
                    evidenceList.add(EvidenceItem("AUDIO", path))
                    evidenceAdapter.notifyDataSetChanged()
                }
            }

        } catch (e: Exception) {
            Log.e("AUDIO", "❌ Save error: ${e.message}")
        }
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        } else fetchLocation()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION])
    private fun fetchLocation() {
        LocationServices.getFusedLocationProviderClient(this)
            .lastLocation.addOnSuccessListener { it?.let { updateLocationUI(it) } }
    }

    private fun updateLocationUI(location: Location) {

        val lat = location.latitude
        val lng = location.longitude

        binding.etLocation.setText("Lat: $lat , Lng: $lng")

        try {

            val geo = Geocoder(this, Locale.getDefault())
            val list = geo.getFromLocation(lat, lng, 1)

            if (!list.isNullOrEmpty()) {

                val area = list[0].subLocality ?: ""
                val city = list[0].locality ?: ""

                binding.etPoliceStation.setText("$area, $city")

            } else {
                binding.etPoliceStation.setText("Fetching location...")
            }

        } catch (e: Exception) {
            Log.e("LOCATION", "Geocoder fail: ${e.message}")
            binding.etPoliceStation.setText("Location unavailable")
        }

        binding.btnSubmit.isEnabled = true
    }

    private fun setupSubmit() {

        binding.btnSubmit.setOnClickListener {

            val fraudType = binding.spFraudType.text.toString()
            val description = binding.etDescription.text.toString()

            if (fraudType.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = FirebaseAuth.getInstance().currentUser

            val complaintId =
                "CMP_${System.currentTimeMillis()}_${(1000..9999).random()}"

            Log.d("SUBMIT", "ID CREATED: $complaintId")

            val entity = ComplaintEntity(
                complaintId = complaintId,
                userId = user?.uid ?: "",
                userEmail = user?.email ?: "",
                fraudType = fraudType,
                description = description,
                location = binding.etLocation.text.toString(),
                policeStation = binding.etPoliceStation.text.toString(),
                evidenceList = Gson().toJson(evidenceList),
                fraudDetails = "{}",
                status = "PENDING"
            )

            lifecycleScope.launch {

                try {

                    // 🔥 STEP 1: SAVE LOCAL
                    AppDatabase.getDatabase(this@ReportCrimeActivity)
                        .complaintDao()
                        .insertComplaint(entity)

                    Log.d("SUBMIT", "Saved in Room")

                    // 🔥 STEP 2: START WORKER
                    val work = OneTimeWorkRequestBuilder<SyncWorker>().build()
                    WorkManager.getInstance(this@ReportCrimeActivity).enqueue(work)

                    Log.d("SUBMIT", "Worker Started")

                    // 🔥 STEP 3: POPUP (UI THREAD)
                    runOnUiThread {

                        AlertDialog.Builder(this@ReportCrimeActivity)
                            .setTitle("Case Created ✅")
                            .setMessage("Your Complaint ID:\n$complaintId")
                            .setCancelable(false)
                            .setPositiveButton("OK") { _, _ ->

                                val intent = Intent(
                                    this@ReportCrimeActivity,
                                    HomeActivity::class.java
                                )

                                intent.flags =
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK

                                startActivity(intent)
                                finish()
                            }
                            .show()
                    }

                } catch (e: Exception) {

                    Log.e("SUBMIT", "ERROR: ${e.message}")

                    runOnUiThread {
                        Toast.makeText(
                            this@ReportCrimeActivity,
                            "Failed to create case",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}