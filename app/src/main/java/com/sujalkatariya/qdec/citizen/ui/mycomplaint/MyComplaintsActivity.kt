package com.sujalkatariya.qdec.citizen.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sujalkatariya.qdec.citizen.DAO.AppDatabase
import com.sujalkatariya.qdec.citizen.citizen.data.mapper.mapToComplaintEntity
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
        listenRealtimeUpdates()
    }

    private fun listenRealtimeUpdates() {

        val user = FirebaseAuth.getInstance().currentUser ?: return

        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("complaints")
            .whereEqualTo("userId", user.uid)
            .addSnapshotListener { snapshot, error ->

                if (error != null) return@addSnapshotListener
                if (snapshot == null) return@addSnapshotListener

                CoroutineScope(Dispatchers.IO).launch {

                    val dao = AppDatabase.getDatabase(this@MyComplaintsActivity)
                        .complaintDao()

                    for (doc in snapshot.documents) {

                        val complaint = mapToComplaintEntity(doc)

                        dao.insertOrUpdate(complaint)
                    }

                    // 🔥 UI refresh automatically
                    val updatedList = dao.getUserComplaints(user.uid)

                    withContext(Dispatchers.Main) {
                        adapter.submitList(updatedList)
                    }
                }
            }
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