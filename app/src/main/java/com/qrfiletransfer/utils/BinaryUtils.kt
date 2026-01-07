package com.qrfiletransfer.utils

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlin.experimental.xor

object BinaryUtils {
    
    // Base64编码（URL安全）
    fun bytesToBase64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP or Base64.URL_SAFE)
    }
    
    fun base64ToBytes(base64: String): ByteArray {
        return Base64.decode(base64, Base64.NO_WRAP or Base64.URL_SAFE)
    }
    
    // Base32编码（可选，用于某些需要字母数字的场景）
    private val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    
    fun bytesToBase32(data: ByteArray): String {
        val output = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                output.append(BASE32_ALPHABET[index])
                bitsLeft -= 5
            }
        }
        
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            output.append(BASE32_ALPHABET[index])
        }
        
        return output.toString()
    }
    
    // 字节数组转十六进制字符串
    fun bytesToHex(data: ByteArray): String {
        val hexChars = CharArray(data.size * 2)
        for (i in data.indices) {
            val v = data[i].toInt() and 0xFF
            hexChars[i * 2] = HEX_ARRAY[v ushr 4]
            hexChars[i * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }
    
    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    
    // 十六进制字符串转字节数组
    fun hexToBytes(hexString: String): ByteArray {
        val len = hexString.length
        val data = ByteArray(len / 2)
        
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) + 
                          Character.digit(hexString[i + 1], 16)).toByte()
        }
        
        return data
    }
    
    // 计算CRC32校验和
    fun calculateCRC32(data: ByteArray): Long {
        var crc: Long = 0xFFFFFFFFL
        val table = crc32Table
        
        for (byte in data) {
            val index = ((crc xor byte.toLong()) and 0xFF).toInt()
            crc = (crc ushr 8) xor table[index]
        }
        
        return crc.inv() and 0xFFFFFFFFL
    }
    
    private val crc32Table: LongArray by lazy {
        LongArray(256).apply {
            for (i in indices) {
                var crc = i.toLong()
                for (j in 0 until 8) {
                    crc = if (crc and 1 == 1L) {
                        0xEDB88320L xor (crc ushr 1)
                    } else {
                        crc ushr 1
                    }
                }
                this[i] = crc
            }
        }
    }
    
    // 添加数据包头部信息
    fun createPacketHeader(
        packetType: Int,
        sequenceNumber: Int,
        totalPackets: Int,
        dataSize: Int
    ): ByteArray {
        return ByteBuffer.allocate(16).apply {
            putInt(packetType)
            putInt(sequenceNumber)
            putInt(totalPackets)
            putInt(dataSize)
        }.array()
    }
    
    // 数据分块
    fun splitIntoChunks(data: ByteArray, chunkSize: Int): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        
        while (offset < data.size) {
            val size = minOf(chunkSize, data.size - offset)
            val chunk = ByteArray(size)
            System.arraycopy(data, offset, chunk, 0, size)
            chunks.add(chunk)
            offset += size
        }
        
        return chunks
    }
    
    // 合并数据块
    fun mergeChunks(chunks: List<ByteArray>): ByteArray {
        val totalSize = chunks.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size
        }
        
        return result
    }
    
    // 添加字节填充
    fun padData(data: ByteArray, blockSize: Int): ByteArray {
        val padding = blockSize - (data.size % blockSize)
        if (padding == blockSize) return data
        
        val padded = ByteArray(data.size + padding)
        System.arraycopy(data, 0, padded, 0, data.size)
        
        for (i in data.size until padded.size) {
            padded[i] = padding.toByte()
        }
        
        return padded
    }
    
    // 移除填充
    fun unpadData(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        
        val padding = data.last().toInt() and 0xFF
        if (padding > data.size) return data
        
        return data.copyOf(data.size - padding)
    }
    
    // 简单的异或加密（用于基本的数据混淆）
    fun xorEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        
        for (i in data.indices) {
            result[i] = data[i] xor key[i % key.size]
        }
        
        return result
    }
    
    fun xorDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        return xorEncrypt(data, key) // 异或加密和解密是相同的操作
    }
    
    // 字节数组转字符串（UTF-8）
    fun bytesToString(data: ByteArray): String {
        return String(data, Charsets.UTF_8)
    }
    
    // 字符串转字节数组（UTF-8）
    fun stringToBytes(string: String): ByteArray {
        return string.toByteArray(Charsets.UTF_8)
    }
    
    // 计算字节数组的校验和（简单的求和）
    fun calculateChecksum(data: ByteArray): Int {
        var sum = 0
        for (byte in data) {
            sum += byte.toInt() and 0xFF
        }
        return sum and 0xFFFF
    }
    
    // 反转字节序（大端小端转换）
    fun reverseBytes(data: ByteArray): ByteArray {
        val reversed = ByteArray(data.size)
        for (i in data.indices) {
            reversed[i] = data[data.size - 1 - i]
        }
        return reversed
    }
    
    // 获取字节数组的子数组
    fun subarray(data: ByteArray, start: Int, end: Int): ByteArray {
        return data.copyOfRange(start, end)
    }
    
    // 比较两个字节数组是否相等
    fun arraysEqual(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i] != b[i]) return false
        }
        return true
    }
    
    // 将整数转换为字节数组
    fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).putInt(value).array()
    }
    
    fun bytesToInt(bytes: ByteArray): Int {
        return ByteBuffer.wrap(bytes).int
    }
    
    // 将长整数转换为字节数组
    fun longToBytes(value: Long): ByteArray {
        return ByteBuffer.allocate(8).putLong(value).array()
    }
    
    fun bytesToLong(bytes: ByteArray): Long {
        return ByteBuffer.wrap(bytes).long
    }
    
    // 将短整数转换为字节数组
    fun shortToBytes(value: Short): ByteArray {
        return ByteBuffer.allocate(2).putShort(value).array()
    }
    
    fun bytesToShort(bytes: ByteArray): Short {
        return ByteBuffer.wrap(bytes).short
    }
}
