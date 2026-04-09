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
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

        setupRecycler()
        setupDropdown()
        setupButtons()
        setupSubmit()

        checkLocationPermission()
    }

    // ---------------- PERMISSION FLOW ----------------

    private fun checkLocationPermission() {

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

        } else {
            fetchLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101) {

            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {

                fetchLocation()

            } else {

                Toast.makeText(
                    this,
                    "Location permission required",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        fetchLocation()
    }

    // ---------------- LOCATION ----------------

    private fun fetchLocation() {

        try {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) return

            val client = LocationServices.getFusedLocationProviderClient(this)

            client.lastLocation
                .addOnSuccessListener { location ->

                    if (location != null) {

                        updateLocationUI(location)

                    } else {

                        Toast.makeText(this, "Turn ON GPS", Toast.LENGTH_SHORT).show()
                    }
                }

        } catch (e: Exception) {
            Log.e("LOCATION", "Error: ${e.message}")
        }
    }

    private fun updateLocationUI(location: Location) {

        val lat = location.latitude
        val lng = location.longitude

        binding.etLocation.setText("Lat: $lat , Lng: $lng")

        try {

            val geo = Geocoder(this, Locale.getDefault())
            val address = geo.getFromLocation(lat, lng, 1)

            val area = address?.get(0)?.subLocality ?: ""
            val city = address?.get(0)?.locality ?: ""

            binding.etPoliceStation.setText("$area, $city")

        } catch (e: Exception) {
            Log.e("LOCATION", "Geocoder fail")
        }

        binding.btnSubmit.isEnabled = true
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
    }

    private fun setupButtons() {

        binding.btnCamera.setOnClickListener { cameraLauncher.launch(null) }
        binding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.btnDocument.setOnClickListener { documentLauncher.launch("*/*") }
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

            val user = FirebaseAuth.getInstance().currentUser

            val complaintId =
                "CMP_${System.currentTimeMillis()}_${(1000..9999).random()}"

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

                AppDatabase.getDatabase(this@ReportCrimeActivity)
                    .complaintDao()
                    .insertComplaint(entity)

                val work = OneTimeWorkRequestBuilder<SyncWorker>().build()

                WorkManager.getInstance(this@ReportCrimeActivity)
                    .enqueue(work)

                Toast.makeText(this@ReportCrimeActivity, "Saved & syncing", Toast.LENGTH_SHORT).show()

                startActivity(Intent(this@ReportCrimeActivity, HomeActivity::class.java))
                finish()
            }
        }
    }
}