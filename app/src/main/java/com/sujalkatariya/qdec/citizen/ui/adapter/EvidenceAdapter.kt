package com.sujalkatariya.qdec.citizen.ui.adapter

import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sujalkatariya.qdec.citizen.R
import com.sujalkatariya.qdec.citizen.citizen.data.model.EvidenceItem
import com.sujalkatariya.qdec.citizen.util.EvidenceEncryptionManager
import java.io.File

class EvidenceAdapter(
    private val list: MutableList<EvidenceItem>,
    private val onClick: (EvidenceItem) -> Unit
) : RecyclerView.Adapter<EvidenceAdapter.EvidenceVH>() {

    class EvidenceVH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgType)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvType: TextView = view.findViewById(R.id.tvType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EvidenceVH {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_evidence_list, parent, false)

        return EvidenceVH(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: EvidenceVH, position: Int) {

        val item = list[position]

        holder.tvTitle.text = "Evidence ${position + 1}"
        holder.tvType.text = item.type

        try {

            when (item.type) {

                // ---------------- IMAGE ----------------
                "IMAGE" -> {

                    val context = holder.itemView.context

                    val extension =
                        item.filePath.substringAfterLast(".", "jpg")

                    val tempFile =
                        File(context.cacheDir, "preview_$position.$extension")

                    // 🔥 STREAM DECRYPT (LIGHT)
                    EvidenceEncryptionManager.decryptToFile(
                        item.filePath,
                        tempFile
                    )

                    val bitmap =
                        BitmapFactory.decodeFile(tempFile.absolutePath)

                    if (bitmap != null) {
                        holder.img.setImageBitmap(bitmap)
                    } else {
                        holder.img.setImageResource(R.drawable.ic_camera)
                    }
                }

                // ---------------- AUDIO ----------------
                "AUDIO" -> {
                    holder.img.setImageResource(R.drawable.ic_mic)
                }

                // ---------------- DOCUMENT ----------------
                "DOCUMENT" -> {
                    holder.img.setImageResource(R.drawable.ic_file)
                }

                else -> {
                    holder.img.setImageResource(R.drawable.ic_file)
                }
            }

        } catch (e: Exception) {

            Log.e("EvidenceAdapter", "Error loading file", e)

            holder.img.setImageResource(R.drawable.ic_file)
            holder.tvType.text = "Error"
        }

        holder.itemView.setOnClickListener {

            try {
                onClick(item)
            } catch (e: Exception) {
                Log.e("EvidenceAdapter", "Click error", e)
            }
        }
    }
}