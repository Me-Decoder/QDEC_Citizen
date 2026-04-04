package com.sujalkatariya.qdec.citizen.ui.report

import android.Manifest
import android.R
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.sujalkatariya.qdec.citizen.DAO.AppDatabase
import com.sujalkatariya.qdec.citizen.citizen.data.model.EvidenceItem
import com.sujalkatariya.qdec.citizen.complaints.ComplaintEntity
import com.sujalkatariya.qdec.citizen.databinding.ActivityReportCrimeBinding
import com.sujalkatariya.qdec.citizen.ui.EvidenceViewerActivity
import com.sujalkatariya.qdec.citizen.ui.adapter.EvidenceAdapter
import com.sujalkatariya.qdec.citizen.ui.home.HomeActivity
import com.sujalkatariya.qdec.citizen.util.EvidenceEncryptionManager
import com.sujalkatariya.qdec.citizen.util.SyncWorker
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

    // ---------------- CAMERA ----------------

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

                evidenceList.add(EvidenceItem("IMAGE", path))
                evidenceAdapter.notifyDataSetChanged()
            }
        }

    // ---------------- GALLERY ----------------

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            uri?.let {

                val path = EvidenceEncryptionManager.saveEncryptedFromUri(
                    this, it, "img_${System.currentTimeMillis()}"
                )

                evidenceList.add(EvidenceItem("IMAGE", path))
                evidenceAdapter.notifyDataSetChanged()
            }
        }

    // ---------------- DOCUMENT ----------------

    private val documentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            uri?.let {

                val path = EvidenceEncryptionManager.saveEncryptedFromUri(
                    this, it, "doc_${System.currentTimeMillis()}"
                )

                evidenceList.add(EvidenceItem("DOCUMENT", path))
                evidenceAdapter.notifyDataSetChanged()
            }
        }

    // ---------------- ON CREATE ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReportCrimeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSubmit.isEnabled = false

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                200
            )
        }

        setupRecycler()
        setupDropdown()
        setupButtons()
        setupSubmit()
        fetchLocation()
    }

    // ---------------- RECYCLER ----------------

    private fun setupRecycler() {

        evidenceAdapter = EvidenceAdapter(evidenceList) {
            openEvidence(it)
        }

        binding.recyclerEvidence.layoutManager =
            LinearLayoutManager(this)

        binding.recyclerEvidence.adapter = evidenceAdapter
    }

    private fun openEvidence(item: EvidenceItem) {

        val intent = Intent(this, EvidenceViewerActivity::class.java)
        intent.putExtra("path", item.filePath)
        intent.putExtra("type", item.type)
        startActivity(intent)
    }

    // ---------------- DROPDOWN ----------------

    private fun setupDropdown() {

        val adapter = ArrayAdapter(
            this,
            R.layout.simple_list_item_1,
            fraudTypes
        )

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

                "Social Media Scam" -> {
                    addField("Platform")
                    addField("Profile Link")
                }

                "Investment Scam" -> {
                    addField("Company Name")
                    addField("Amount Invested")
                }

                "Other" -> addField("Details")
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

    // ---------------- BUTTONS ----------------

    private fun setupButtons() {

        binding.btnCamera.setOnClickListener { cameraLauncher.launch(null) }
        binding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.btnDocument.setOnClickListener { documentLauncher.launch("*/*") }

        binding.btnAudio.setOnClickListener {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Allow mic permission first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isRecording) {
                startRecording()
                Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show()
            } else {
                stopRecording()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------- SUBMIT ----------------

    private fun setupSubmit() {

        binding.btnSubmit.setOnClickListener {

            val fraudType = binding.spFraudType.text.toString()
            val description = binding.etDescription.text.toString()

            if (fraudType.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binding.etLocation.text.toString().isEmpty()) {
                Toast.makeText(this, "Location required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (evidenceList.isEmpty()) {
                Toast.makeText(this, "Add at least 1 evidence", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val hasAudio = evidenceList.any { it.type == "AUDIO" }

            if (!hasAudio) {
                Toast.makeText(this, "Audio required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fraudDetailsMap = HashMap<String, String>()

            for (i in 0 until binding.layoutFraudFields.childCount) {

                val layout = binding.layoutFraudFields.getChildAt(i) as TextInputLayout
                val edit = layout.editText

                val key = edit?.tag.toString()
                val value = edit?.text.toString()

                fraudDetailsMap[key] =
                    EvidenceEncryptionManager.encryptText(value) ?: ""
            }

            val encryptedDescription =
                EvidenceEncryptionManager.encryptText(description) ?: ""

            val user = FirebaseAuth.getInstance().currentUser

            val complaintId =
                "CMP_${System.currentTimeMillis()}_${(1000..9999).random()}"

            val entity = ComplaintEntity(
                complaintId = complaintId,
                userId = user?.uid ?: "",
                userEmail = user?.email ?: "",
                fraudType = fraudType,
                description = encryptedDescription,
                location = binding.etLocation.text.toString(),
                policeStation = binding.etPoliceStation.text.toString(),
                evidenceList = Gson().toJson(evidenceList),
                fraudDetails = Gson().toJson(fraudDetailsMap),
                status = "PENDING"
            )

            lifecycleScope.launch {

                AppDatabase.getDatabase(this@ReportCrimeActivity)
                    .complaintDao()
                    .insertComplaint(entity)

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val work =
                    OneTimeWorkRequestBuilder<SyncWorker>()
                        .setConstraints(constraints)
                        .build()

                WorkManager.getInstance(this@ReportCrimeActivity)
                    .enqueue(work)

                Toast.makeText(
                    this@ReportCrimeActivity,
                    "Saved locally & syncing...",
                    Toast.LENGTH_SHORT
                ).show()

                binding.btnSubmit.isEnabled = false // 🚫 prevent multiple clicks

                AlertDialog.Builder(this@ReportCrimeActivity)
                    .setTitle("Case Created ✅")
                    .setMessage("Your Complaint ID:\n$complaintId")
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ ->

                        // 👉 Go back to main screen
                        val intent = Intent(this@ReportCrimeActivity, HomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)

                        finish() // 🔥 close current screen
                    }
                    .show()
            }
        }
    }

    // ---------------- RECORDING ----------------

    private fun startRecording() {

        try {

            audioFile = File(cacheDir, "audio_${System.currentTimeMillis()}.m4a")

            mediaRecorder = MediaRecorder().apply {

                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)

                prepare()
                start()
            }

            isRecording = true
            startMicPulse()

        } catch (e: Exception) {

            e.printStackTrace()
            Toast.makeText(this, "Mic error / permission issue", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {

        mediaRecorder?.apply {
            stop()
            release()
        }

        mediaRecorder = null
        isRecording = false

        stopMicPulse()

        val path = EvidenceEncryptionManager.saveEncryptedFromUri(
            this,
            Uri.fromFile(audioFile),
            "audio_${System.currentTimeMillis()}"
        )

        evidenceList.add(EvidenceItem("AUDIO", path))
        evidenceAdapter.notifyDataSetChanged()
    }

    // ---------------- MIC ANIMATION ----------------

    private fun startMicPulse() {

        binding.micPulse.visibility = View.VISIBLE

        pulseAnimator =
            ObjectAnimator.ofFloat(binding.micPulse, "alpha", 0.3f, 1f).apply {

                duration = 600L

                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE

                start()
            }
    }

    private fun stopMicPulse() {

        pulseAnimator?.cancel()
        binding.micPulse.visibility = View.GONE
    }

    // ---------------- LOCATION ----------------

    private fun fetchLocation() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                101
            )
            return
        }

        LocationServices.getFusedLocationProviderClient(this)
            .lastLocation
            .addOnSuccessListener { updateLocationUI(it) }
    }

    private fun updateLocationUI(location: Location?) {

        if (location == null) {
            Toast.makeText(this, "Turn ON location", Toast.LENGTH_SHORT).show()
            binding.btnSubmit.isEnabled = false
            return
        }

        val lat = location.latitude
        val lng = location.longitude

        // 🔥 1. FIRST FIELD → LAT LONG
        binding.etLocation.setText("Lat: $lat , Lng: $lng")

        // 🔥 2. SECOND FIELD → AREA + CITY
        val geo = Geocoder(this, Locale.getDefault())
        val address = geo.getFromLocation(lat, lng, 1)

        val area = address?.get(0)?.subLocality ?: ""
        val city = address?.get(0)?.locality ?: ""

        binding.etPoliceStation.setText("$area, $city")

        binding.btnSubmit.isEnabled = true
    }
}