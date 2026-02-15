package com.example.model3dviewer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_models")
data class RecentModel(
    @PrimaryKey val id: Long,
    val name: String,
    val path: String,
    val thumbnailPath: String? = null,
    val lastOpened: Long,
    val polygonCount: Int,
    val fileSize: Long = 0
)
