package com.example.model3dviewer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.model3dviewer.R
import com.example.model3dviewer.model.RecentModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ModelThumbnailAdapter(
    private val onItemClick: (RecentModel) -> Unit,
    private val onItemLongClick: (RecentModel) -> Boolean
) : ListAdapter<RecentModel, ModelThumbnailAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_thumbnail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvPolyCount: TextView = itemView.findViewById(R.id.tvPolyCount)

        fun bind(model: RecentModel) {
            itemView.tag = model.id
            tvName.text = model.name
            tvDate.text = formatDate(model.lastOpened)
            tvPolyCount.text = if (model.polygonCount > 0) "${model.polygonCount / 1000}K 面" else "未知"

            if (model.thumbnailPath != null && File(model.thumbnailPath).exists()) {
                Glide.with(itemView.context)
                    .load(model.thumbnailPath)
                    .centerCrop()
                    .placeholder(R.drawable.placeholder_model)
                    .into(ivThumbnail)
            } else {
                Glide.with(itemView.context)
                    .load(R.drawable.placeholder_model)
                    .centerCrop()
                    .into(ivThumbnail)
            }

            itemView.setOnClickListener { onItemClick(model) }
            itemView.setOnLongClickListener { onItemLongClick(model) }
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RecentModel>() {
        override fun areItemsTheSame(oldItem: RecentModel, newItem: RecentModel) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RecentModel, newItem: RecentModel) = oldItem == newItem
    }
}
