package com.qrfiletransfer.model

import java.util.Date

data class TransferStats(
    val totalFiles: Int = 0,
    val totalSize: Long = 0,
    val successfulTransfers: Int = 0,
    val failedTransfers: Int = 0,
    val averageSpeed: Double = 0.0,
    val totalTime: Long = 0,
    val lastTransferTime: Long? = null,
    val chunkStats: ChunkStats = ChunkStats()
) {
    fun getSuccessRate(): Double {
        val total = successfulTransfers + failedTransfers
        return if (total > 0) {
            successfulTransfers.toDouble() / total.toDouble() * 100.0
        } else {
            0.0
        }
    }
    
    fun getAverageFileSize(): Double {
        return if (totalFiles > 0) {
            totalSize.toDouble() / totalFiles
        } else {
            0.0
        }
    }
    
    fun getAverageTransferTime(): Double {
        return if (successfulTransfers > 0) {
            totalTime.toDouble() / successfulTransfers
        } else {
            0.0
        }
    }
    
    fun getFormattedLastTransferTime(): String {
        return lastTransferTime?.let { Date(it).toString() } ?: "暂无记录"
    }
    
    fun getFormattedTotalSize(): String {
        return formatFileSize(totalSize)
    }
    
    fun getFormattedAverageSpeed(): String {
        return formatSpeed(averageSpeed)
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
    
    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024.0)
            else -> String.format("%.2f B/s", bytesPerSecond)
        }
    }
}

data class ChunkStats(
    val totalChunks: Int = 0,
    val successfulChunks: Int = 0,
    val failedChunks: Int = 0,
    val retriedChunks: Int = 0,
    val averageChunkTime: Long = 0,
    val minChunkTime: Long = Long.MAX_VALUE,
    val maxChunkTime: Long = 0
) {
    fun getChunkSuccessRate(): Double {
        return if (totalChunks > 0) {
            successfulChunks.toDouble() / totalChunks.toDouble() * 100.0
        } else {
            0.0
        }
    }
    
    fun getRetryRate(): Double {
        return if (totalChunks > 0) {
            retriedChunks.toDouble() / totalChunks.toDouble() * 100.0
        } else {
            0.0
        }
    }
    
    fun getAverageChunkTimeInMs(): Long {
        return averageChunkTime
    }
    
    fun getChunkTimeRange(): String {
        return if (minChunkTime == Long.MAX_VALUE || maxChunkTime == 0L) {
            "N/A"
        } else {
            "${minChunkTime}ms - ${maxChunkTime}ms"
        }
    }
}
