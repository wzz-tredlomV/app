package com.qrfiletransfer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.Date
import java.util.UUID

@Entity(tableName = "file_chunks")
data class FileChunk(
    @PrimaryKey
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    
    @SerializedName("file_id")
    val fileId: String,
    
    @SerializedName("session_id")
    val sessionId: String,
    
    @SerializedName("chunk_index")
    val chunkIndex: Int,
    
    @SerializedName("total_chunks")
    val totalChunks: Int,
    
    @SerializedName("data")
    val data: String,
    
    @SerializedName("data_hash")
    val dataHash: String,
    
    @SerializedName("file_hash")
    val fileHash: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("qr_version")
    val qrVersion: Int,
    
    @SerializedName("error_correction_level")
    val errorCorrectionLevel: String,
    
    @SerializedName("parity_bytes")
    val parityBytes: String? = null,
    
    @SerializedName("is_compressed")
    val isCompressed: Boolean,
    
    @SerializedName("compression_ratio")
    val compressionRatio: Double? = null,
    
    @SerializedName("file_name")
    val fileName: String,
    
    @SerializedName("file_size")
    val fileSize: Long,
    
    @SerializedName("is_transmitted")
    var isTransmitted: Boolean = false,
    
    @SerializedName("is_received")
    var isReceived: Boolean = false,
    
    @SerializedName("is_verified")
    var isVerified: Boolean = false
) {
    companion object {
        fun fromJson(json: String): FileChunk {
            return Gson().fromJson(json, FileChunk::class.java)
        }
    }
    
    fun toJson(): String {
        return Gson().toJson(this)
    }
    
    fun getEstimatedQRSize(): Int {
        return this.toJson().toByteArray(Charsets.UTF_8).size
    }
    
    fun getFormattedTimestamp(): String {
        return Date(timestamp).toString()
    }
    
    fun getDataSize(): Int {
        return data.length
    }
    
    fun getChunkInfo(): String {
        return "Chunk ${chunkIndex + 1}/$totalChunks"
    }
}
