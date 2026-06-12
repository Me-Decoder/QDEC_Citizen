package com.sujalkatariya.qdec2.citizen.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.sujalkatariya.qdec2.citizen.R
import com.sujalkatariya.qdec2.citizen.databinding.ActivityHomeBinding
import com.sujalkatariya.qdec.citizen.ui.MyComplaintsActivity
import com.sujalkatariya.qdec2.citizen.ui.report.ReportCrimeActivity
import com.sujalkatariya.qdec2.citizen.util.SyncWorker
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    private fun startAutoSync() {

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work =
            PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "sync_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
    }

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)



        startAutoSync()
        loadUser()
        setupClicks()
    }

    // 🔥 USER DATA LOAD
    private fun loadUser() {

        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {

            val name = user.displayName ?: "Citizen"
            binding.tvUser.text = name

            val photoUrl = user.photoUrl

            Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.ic_police_hat)
                .error(R.drawable.ic_police_hat)
                .fallback(R.drawable.ic_police_hat)
                .circleCrop()
                .into(binding.imgProfile)

        } else {
            binding.tvUser.text = "Guest User"

            // fallback image
            binding.imgProfile.setImageResource(R.drawable.ic_police_hat)
        }
    }

    // 🔥 BUTTON CLICKS
    private fun setupClicks() {

        // REPORT CRIME
        binding.cardReport.setOnClickListener {
            startActivity(Intent(this, ReportCrimeActivity::class.java))
        }

        // MY COMPLAINTS
        binding.cardComplaints.setOnClickListener {
            startActivity(Intent(this, MyComplaintsActivity::class.java))
        }

        // EMERGENCY CALL
        binding.btnEmergency.setOnClickListener {

            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:1930")

            startActivity(intent)
        }
    }
}