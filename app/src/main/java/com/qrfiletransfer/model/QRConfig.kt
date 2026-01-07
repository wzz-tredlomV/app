package com.qrfiletransfer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qr_configs")
data class QRConfig(
    @PrimaryKey
    val id: Int = 1,
    
    val minVersion: Int = 1,
    val maxVersion: Int = 40,
    val errorCorrectionLevel: String = "M",
    val compressionThreshold: Long = 1024L,
    val compressionAlgorithm: String = "LZ4",
    val chunkSizeLimit: Int = 2000,
    val qrDisplayDuration: Long = 500L,
    val retryCount: Int = 3,
    val adaptiveQR: Boolean = true,
    val dynamicChunkSize: Boolean = true,
    val autoResume: Boolean = true,
    val verifyEachChunk: Boolean = true,
    val saveLocation: String = "downloads/QRFileTransfer",
    val tempFileExpiryDays: Int = 7,
    val enableNotifications: Boolean = true,
    val vibrationOnComplete: Boolean = true,
    val soundOnComplete: Boolean = true,
    val themeMode: String = "system"
) {
    companion object {
        fun getDefault(): QRConfig {
            return QRConfig()
        }
        
        fun getHighQualityConfig(): QRConfig {
            return QRConfig(
                errorCorrectionLevel = "H",
                compressionThreshold = 512L,
                chunkSizeLimit = 1500,
                qrDisplayDuration = 800L,
                retryCount = 5,
                verifyEachChunk = true
            )
        }
        
        fun getFastConfig(): QRConfig {
            return QRConfig(
                errorCorrectionLevel = "L",
                compressionThreshold = 2048L,
                chunkSizeLimit = 2500,
                qrDisplayDuration = 300L,
                retryCount = 1,
                verifyEachChunk = false
            )
        }
        
        fun getBalancedConfig(): QRConfig {
            return QRConfig(
                errorCorrectionLevel = "M",
                compressionThreshold = 1024L,
                chunkSizeLimit = 2000,
                qrDisplayDuration = 500L,
                retryCount = 3,
                verifyEachChunk = true
            )
        }
    }
    
    fun getErrorCorrectionPercentage(): Int {
        return when (errorCorrectionLevel) {
            "L" -> 7
            "M" -> 15
            "Q" -> 25
            "H" -> 30
            else -> 15
        }
    }
    
    fun getCompressionAlgorithmName(): String {
        return when (compressionAlgorithm) {
            "LZ4" -> "LZ4 (快速)"
            "DEFLATE" -> "DEFLATE (平衡)"
            "GZIP" -> "GZIP (高压缩)"
            "NONE" -> "无压缩"
            else -> compressionAlgorithm
        }
    }
    
    fun getDisplayTimeInSeconds(): Double {
        return qrDisplayDuration / 1000.0
    }
    
    fun getChunkSizeInKB(): Double {
        return chunkSizeLimit / 1024.0
    }
    
    fun getCompressionThresholdInKB(): Double {
        return compressionThreshold / 1024.0
    }
    
    fun isValid(): Boolean {
        return minVersion in 1..40 &&
               maxVersion in 1..40 &&
               minVersion <= maxVersion &&
               errorCorrectionLevel in listOf("L", "M", "Q", "H") &&
               chunkSizeLimit in 500..2953 &&
               qrDisplayDuration in 100..5000 &&
               retryCount in 0..10 &&
               tempFileExpiryDays in 1..30
    }
}
