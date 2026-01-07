package com.qrfiletransfer.receiver

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qrfiletransfer.model.TransferStatus
import java.util.*

class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
    
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toStringList(json: String?): List<String>? {
        return json?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(it, type)
        }
    }
    
    @TypeConverter
    fun fromIntList(list: List<Int>?): String? {
        return list?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toIntList(json: String?): List<Int>? {
        return json?.let {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson(it, type)
        }
    }
    
    @TypeConverter
    fun fromTransferStatus(status: TransferStatus?): String? {
        return status?.name
    }
    
    @TypeConverter
    fun toTransferStatus(name: String?): TransferStatus? {
        return name?.let { TransferStatus.valueOf(it) }
    }
    
    @TypeConverter
    fun fromMap(map: Map<String, Any>?): String? {
        return map?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toMap(json: String?): Map<String, Any>? {
        return json?.let {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(it, type)
        }
    }
    
    @TypeConverter
    fun fromByteArray(bytes: ByteArray?): String? {
        return bytes?.let { Base64.getEncoder().encodeToString(it) }
    }
    
    @TypeConverter
    fun toByteArray(base64: String?): ByteArray? {
        return base64?.let { Base64.getDecoder().decode(it) }
    }
}
