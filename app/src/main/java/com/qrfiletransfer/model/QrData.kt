package com.qrfiletransfer.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class QrData(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("data")
    val data: String,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("version")
    val version: String = "1.0",
    
    @SerializedName("checksum")
    val checksum: String? = null
) {
    companion object {
        const val TYPE_CHUNK = "chunk"
        const val TYPE_METADATA = "metadata"
        const val TYPE_CONTROL = "control"
        const val TYPE_RESUME = "resume"
        
        fun fromChunk(chunk: FileChunk): QrData {
            return QrData(
                type = TYPE_CHUNK,
                data = chunk.toJson(),
                checksum = calculateChecksum(chunk.toJson())
            )
        }
        
        fun fromJson(json: String): QrData {
            return Gson().fromJson(json, QrData::class.java)
        }
        
        private fun calculateChecksum(data: String): String {
            // 简单校验和计算
            var sum = 0
            for (char in data) {
                sum += char.code
            }
            return sum.toString(16)
        }
    }
    
    fun toJson(): String {
        return Gson().toJson(this)
    }
    
    fun isValid(): Boolean {
        if (checksum == null) return true
        
        val calculated = calculateChecksum(data)
        return checksum == calculated
    }
    
    private fun calculateChecksum(data: String): String {
        var sum = 0
        for (char in data) {
            sum += char.code
        }
        return sum.toString(16)
    }
}
