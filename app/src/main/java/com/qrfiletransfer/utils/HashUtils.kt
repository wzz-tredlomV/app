package com.qrfiletransfer.utils

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HashUtils {
    
    // SHA-256
    fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return BinaryUtils.bytesToHex(hash)
    }
    
    fun sha256(data: String): String {
        return sha256(data.toByteArray(Charsets.UTF_8))
    }
    
    // SHA-3 (更安全的哈希算法)
    fun sha3_256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA3-256")
        val hash = digest.digest(data)
        return BinaryUtils.bytesToHex(hash)
    }
    
    // HMAC-SHA256
    fun hmacSha256(data: ByteArray, key: ByteArray): String {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        val secretKey = SecretKeySpec(key, algorithm)
        mac.init(secretKey)
        val hash = mac.doFinal(data)
        return BinaryUtils.bytesToHex(hash)
    }
    
    // 计算文件的哈希（分块计算，适合大文件）
    fun calculateFileHash(filePath: String): String {
        val buffer = ByteArray(8192)
        val digest = MessageDigest.getInstance("SHA-256")
        
        java.io.File(filePath).inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        
        val hash = digest.digest()
        return BinaryUtils.bytesToHex(hash)
    }
    
    // 计算文件的Merkle树根哈希
    fun calculateMerkleRoot(chunks: List<ByteArray>): String {
        if (chunks.isEmpty()) return ""
        
        val leaves = chunks.map { sha256(it) }
        
        var currentLevel = leaves
        
        while (currentLevel.size > 1) {
            val nextLevel = mutableListOf<String>()
            
            for (i in currentLevel.indices step 2) {
                val left = currentLevel[i]
                val right = if (i + 1 < currentLevel.size) currentLevel[i + 1] else left
                
                val combined = sha256("$left$right")
                nextLevel.add(combined)
            }
            
            currentLevel = nextLevel
        }
        
        return currentLevel.first()
    }
    
    // 验证Merkle证明
    fun verifyMerkleProof(
        leafHash: String,
        rootHash: String,
        proof: List<Pair<String, Boolean>>
    ): Boolean {
        var currentHash = leafHash
        
        for ((siblingHash, isLeft) in proof) {
            currentHash = if (isLeft) {
                sha256("${siblingHash}${currentHash}")
            } else {
                sha256("${currentHash}${siblingHash}")
            }
        }
        
        return currentHash == rootHash
    }
    
    // CRC32快速校验（用于初步验证）
    fun crc32(data: ByteArray): Long {
        return BinaryUtils.calculateCRC32(data)
    }
    
    // 创建文件的唯一标识符
    fun createFileId(filePath: String, fileSize: Long): String {
        val fileInfo = "$filePath:$fileSize:${System.currentTimeMillis()}"
        return sha256(fileInfo)
    }
    
    // 验证哈希匹配
    fun verifyHash(data: ByteArray, expectedHash: String): Boolean {
        val actualHash = sha256(data)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }
    
    // 快速哈希（用于内部标识）
    fun quickHash(data: ByteArray): String {
        return sha256(data).substring(0, 8)
    }
    
    // 计算数据块的哈希链
    fun calculateHashChain(chunks: List<ByteArray>): List<String> {
        val hashes = mutableListOf<String>()
        var previousHash = ""
        
        for (chunk in chunks) {
            val combined = if (previousHash.isEmpty()) {
                sha256(chunk)
            } else {
                sha256(previousHash + sha256(chunk))
            }
            hashes.add(combined)
            previousHash = combined
        }
        
        return hashes
    }
    
    // 验证哈希链
    fun verifyHashChain(chunks: List<ByteArray>, chain: List<String>): Boolean {
        if (chunks.size != chain.size) return false
        
        var previousHash = ""
        for (i in chunks.indices) {
            val expectedHash = if (previousHash.isEmpty()) {
                sha256(chunks[i])
            } else {
                sha256(previousHash + sha256(chunks[i]))
            }
            
            if (expectedHash != chain[i]) {
                return false
            }
            
            previousHash = chain[i]
        }
        
        return true
    }
    
    // 计算数据的指纹（短哈希）
    fun fingerprint(data: ByteArray, length: Int = 16): String {
        val hash = sha256(data)
        return hash.substring(0, length.coerceAtMost(hash.length))
    }
    
    // 生成随机盐值
    fun generateSalt(length: Int = 16): ByteArray {
        val salt = ByteArray(length)
        java.security.SecureRandom().nextBytes(salt)
        return salt
    }
    
    // 带盐的哈希
    fun saltedHash(data: ByteArray, salt: ByteArray): String {
        val combined = ByteArray(data.size + salt.size)
        System.arraycopy(data, 0, combined, 0, data.size)
        System.arraycopy(salt, 0, combined, data.size, salt.size)
        return sha256(combined)
    }
}
