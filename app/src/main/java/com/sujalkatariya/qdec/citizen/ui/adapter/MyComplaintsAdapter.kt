package com.sujalkatariya.qdec.citizen.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sujalkatariya.qdec.citizen.complaints.ComplaintEntity
import com.sujalkatariya.qdec.citizen.databinding.ItemComplaintBinding
import androidx.core.graphics.toColorInt

class MyComplaintsAdapter :
    RecyclerView.Adapter<MyComplaintsAdapter.VH>() {

    private var list = listOf<ComplaintEntity>()

    fun submitList(newList: List<ComplaintEntity>) {
        list = newList
        notifyDataSetChanged()
    }

    class VH(val binding: ItemComplaintBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {

        val binding = ItemComplaintBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return VH(binding)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {

        val item = list[position]

        holder.binding.tvId.text = item.complaintId
        holder.binding.tvType.text = item.fraudType
        holder.binding.tvLocation.text = item.location

        // 🔥 STATUS FIX (REAL FIREBASE STATUS)
        val status = item.status.uppercase()

        holder.binding.tvStatus.text = status

        when (status) {
            "ASSIGNED" -> holder.binding.tvStatus.setTextColor("#2196F3".toColorInt()) // Blue
            "PENDING" -> holder.binding.tvStatus.setTextColor("#FFA000".toColorInt()) // Orange
            "CLOSED" -> holder.binding.tvStatus.setTextColor("#4CAF50".toColorInt()) // Green
            else -> holder.binding.tvStatus.setTextColor(Color.GRAY)
        }

        // 🔥 OFFICER NAME SHOW (MAIN FEATURE 🔥)
        val officerName = item.assignedOfficerName

        if (!officerName.isNullOrEmpty()) {
            holder.binding.tvOfficer.text = "Assigned to: $officerName"
        } else {
            holder.binding.tvOfficer.text = "Not assigned yet"
        }
    }
}