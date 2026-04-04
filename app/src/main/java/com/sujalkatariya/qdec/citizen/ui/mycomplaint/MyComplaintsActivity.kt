package com.sujalkatariya.qdec.citizen.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.sujalkatariya.qdec.citizen.DAO.AppDatabase
import com.sujalkatariya.qdec.citizen.databinding.ActivityMyComplaintsBinding
import com.sujalkatariya.qdec.citizen.ui.adapter.MyComplaintsAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyComplaintsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyComplaintsBinding
    private lateinit var adapter: MyComplaintsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMyComplaintsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MyComplaintsAdapter()

        binding.recyclerComplaints.layoutManager =
            LinearLayoutManager(this)

        binding.recyclerComplaints.adapter = adapter

        loadComplaints()
    }

    private fun loadComplaints() {

        val user = FirebaseAuth.getInstance().currentUser

        CoroutineScope(Dispatchers.IO).launch {

            val list = AppDatabase.getDatabase(this@MyComplaintsActivity)
                .complaintDao()
                .getUserComplaints(user?.uid ?: "")

            withContext(Dispatchers.Main) {
                adapter.submitList(list)
            }
        }
    }
}