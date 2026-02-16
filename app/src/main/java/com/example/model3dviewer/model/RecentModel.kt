// RecentModel.kt
package com.example.model3dviewer.model

import kotlinx.serialization.Serializable

@Serializable
data class RecentModel(
    val id: String,
    val name: String,
    val path: String,
    val thumbnailPath: String? = null,
    val lastOpened: Long,
    val polygonCount: Int = 0,
    val fileSize: Long = 0
)
