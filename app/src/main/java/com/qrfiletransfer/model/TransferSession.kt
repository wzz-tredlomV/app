package com.qrfiletransfer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "transfer_sessions")
data class TransferSession(
    @PrimaryKey
    val id: String,
    
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val fileHash: String,
    val totalChunks: Int,
    val chunksTransmitted: Int = 0,
    val chunksReceived: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val status: TransferStatus = TransferStatus.PENDING,
    val compressionEnabled: Boolean = true,
    val errorCorrectionEnabled: Boolean = true,
    val qrVersion: Int = 40,
    val lastChunkIndex: Int = -1,
    val transmissionSpeed: Double = 0.0,
    val resumeToken: String = UUID.randomUUID().toString(),
    val errorMessage: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3
) {
    fun calculateProgress(): Double {
        return if (totalChunks > 0) {
            chunksTransmitted.toDouble() / totalChunks.toDouble() * 100.0
        } else {
            0.0
        }
    }
    
    fun calculateReceiveProgress(): Double {
        return if (totalChunks > 0) {
            chunksReceived.toDouble() / totalChunks.toDouble() * 100.0
        } else {
            0.0
        }
    }
    
    fun isComplete(): Boolean {
        return chunksTransmitted >= totalChunks
    }
    
    fun isReceivedComplete(): Boolean {
        return chunksReceived >= totalChunks
    }
    
    fun getEstimatedRemainingTime(): Long {
        return if (transmissionSpeed > 0) {
            ((totalChunks - chunksTransmitted) * 1000L / transmissionSpeed).toLong()
        } else {
            0
        }
    }
    
    fun getDuration(): Long {
        return if (endTime != null) {
            endTime - startTime
        } else {
            System.currentTimeMillis() - startTime
        }
    }
    
    fun getFormattedStartTime(): String {
        return Date(startTime).toString()
    }
    
    fun getFormattedEndTime(): String {
        return endTime?.let { Date(it).toString() } ?: "尚未完成"
    }
    
    fun getFormattedDuration(): String {
        val duration = getDuration()
        val seconds = duration / 1000
        
        return when {
            seconds >= 3600 -> {
                val hours = seconds / 3600
                val mins = (seconds % 3600) / 60
                val secs = seconds % 60
                "${hours}h ${mins}m ${secs}s"
            }
            seconds >= 60 -> {
                val mins = seconds / 60
                val secs = seconds % 60
                "${mins}m ${secs}s"
            }
            else -> "${seconds}s"
        }
    }
    
    fun canRetry(): Boolean {
        return retryCount < maxRetries && status == TransferStatus.FAILED
    }
    
    fun markAsStarted() {
        // 在实际实现中，这里会更新数据库
    }
    
    fun markAsCompleted() {
        // 在实际实现中，这里会更新数据库
    }
    
    fun markAsFailed(error: String) {
        // 在实际实现中，这里会更新数据库
    }
}

enum class TransferStatus {
    PENDING,
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED,
    RESUMING,
    CANCELLED
}
