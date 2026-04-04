package com.sujalkatariya.qdec.citizen.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sujalkatariya.qdec.citizen.complaints.ComplaintEntity
import com.sujalkatariya.qdec.citizen.databinding.ItemComplaintBinding

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

        // 🔥 STATUS COLOR
        if (item.status == "SYNCED") {
            holder.binding.tvStatus.text = "SYNCED"
            holder.binding.tvStatus.setTextColor(Color.GREEN)
        } else {
            holder.binding.tvStatus.text = "PENDING"
            holder.binding.tvStatus.setTextColor(Color.YELLOW)
        }
    }
}