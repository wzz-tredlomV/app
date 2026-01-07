package com.qrfiletransfer.sender

import com.qrfiletransfer.utils.ReedSolomon

/**
 * 错误纠正管理器
 * 在二维码基础上添加额外的纠错码，提高传输可靠性
 */
class ErrorCorrection {
    
    companion object {
        // 推荐的纠错配置
        val PRESET_CONFIGS = mapOf(
            "LOW" to ErrorCorrectionConfig(10, 2),    // 20% 冗余
            "MEDIUM" to ErrorCorrectionConfig(10, 3),  // 30% 冗余
            "HIGH" to ErrorCorrectionConfig(10, 4),    // 40% 冗余
            "VERY_HIGH" to ErrorCorrectionConfig(10, 5) // 50% 冗余
        )
    }
    
    /**
     * 为数据添加纠错码
     */
    fun addErrorCorrection(
        data: ByteArray,
        config: ErrorCorrectionConfig = PRESET_CONFIGS["MEDIUM"]!!
    ): ErrorCorrectionResult {
        // 将数据分块
        val chunkSize = 255  // Reed-Solomon GF(2^8) 的最大块大小
        val chunks = data.chunked(chunkSize)
        
        val rs = ReedSolomon(config.dataShards, config.parityShards)
        val allShards = mutableListOf<ByteArray>()
        
        for (chunk in chunks) {
            // 准备数据块
            val dataShards = Array<ByteArray?>(config.dataShards) { null }
            val paddedChunk = chunk.toByteArray()
            
            // 如果最后一个块不足，填充0
            if (paddedChunk.size < chunkSize * config.dataShards) {
                val fullSize = chunkSize * config.dataShards
                val padded = ByteArray(fullSize)
                System.arraycopy(paddedChunk, 0, padded, 0, paddedChunk.size)
                // 剩余部分保持为0
                
                for (i in 0 until config.dataShards) {
                    val shard = ByteArray(chunkSize)
                    System.arraycopy(padded, i * chunkSize, shard, 0, chunkSize)
                    dataShards[i] = shard
                }
            } else {
                for (i in 0 until config.dataShards) {
                    val shard = ByteArray(chunkSize)
                    System.arraycopy(paddedChunk, i * chunkSize, shard, 0, chunkSize)
                    dataShards[i] = shard
                }
            }
            
            // 编码
            val encoded = rs.encode(dataShards)
            
            // 只取校验块
            val parityShards = encoded.sliceArray(config.dataShards until encoded.size)
            
            // 合并校验数据
            val parityData = ByteArray(config.parityShards * chunkSize)
            for (i in parityShards.indices) {
                System.arraycopy(
                    parityShards[i] ?: ByteArray(chunkSize),
                    0,
                    parityData,
                    i * chunkSize,
                    chunkSize
                )
            }
            
            allShards.add(parityData)
        }
        
        // 合并所有校验数据
        val totalParitySize = allShards.sumOf { it.size }
        val parityData = ByteArray(totalParitySize)
        var offset = 0
        
        for (shard in allShards) {
            System.arraycopy(shard, 0, parityData, offset, shard.size)
            offset += shard.size
        }
        
        return ErrorCorrectionResult(
            originalData = data,
            parityData = parityData,
            config = config,
            totalSize = data.size + parityData.size
        )
    }
    
    /**
     * 尝试恢复损坏的数据
     */
    fun recoverData(
        data: ByteArray,
        parityData: ByteArray,
        config: ErrorCorrectionConfig,
        damagedIndices: List<Int>
    ): RecoveryResult {
        val chunkSize = 255
        val dataChunks = data.chunked(chunkSize * config.dataShards)
        val parityChunks = parityData.chunked(chunkSize * config.parityShards)
        
        val rs = ReedSolomon(config.dataShards, config.parityShards)
        val recoveredData = mutableListOf<ByteArray>()
        var recoveredChunks = 0
        
        for (i in dataChunks.indices) {
            val dataChunk = dataChunks[i].toByteArray()
            val parityChunk = if (i < parityChunks.size) parityChunks[i].toByteArray() else ByteArray(0)
            
            // 准备所有块（数据和校验）
            val allShards = Array<ByteArray?>(config.dataShards + config.parityShards) { null }
            
            // 填充数据块
            for (j in 0 until config.dataShards) {
                if (j * chunkSize < dataChunk.size) {
                    val shard = ByteArray(chunkSize)
                    val copySize = minOf(chunkSize, dataChunk.size - j * chunkSize)
                    System.arraycopy(dataChunk, j * chunkSize, shard, 0, copySize)
                    allShards[j] = shard
                }
            }
            
            // 填充校验块
            for (j in 0 until config.parityShards) {
                if (j * chunkSize < parityChunk.size) {
                    val shard = ByteArray(chunkSize)
                    val copySize = minOf(chunkSize, parityChunk.size - j * chunkSize)
                    System.arraycopy(parityChunk, j * chunkSize, shard, 0, copySize)
                    allShards[config.dataShards + j] = shard
                }
            }
            
            // 标记哪些块是损坏的（在这个简化版本中，我们假设知道损坏的位置）
            val shardPresent = BooleanArray(allShards.size) { true }
            
            // 尝试解码
            try {
                val decoded = rs.decode(allShards, shardPresent)
                
                // 提取恢复的数据
                val recoveredChunk = ByteArray(config.dataShards * chunkSize)
                for (j in 0 until config.dataShards) {
                    decoded[j]?.let { shard ->
                        System.arraycopy(shard, 0, recoveredChunk, j * chunkSize, chunkSize)
                    }
                }
                
                recoveredData.add(recoveredChunk)
                recoveredChunks++
            } catch (e: Exception) {
                // 恢复失败
                recoveredData.add(dataChunk)
            }
        }
        
        // 合并所有恢复的数据块
        val totalSize = recoveredData.sumOf { it.size }
        val finalData = ByteArray(totalSize)
        var offset = 0
        
        for (chunk in recoveredData) {
            System.arraycopy(chunk, 0, finalData, offset, chunk.size)
            offset += chunk.size
        }
        
        return RecoveryResult(
            recoveredData = finalData,
            successRate = recoveredChunks.toDouble() / dataChunks.size.toDouble(),
            recoveredChunks = recoveredChunks,
            totalChunks = dataChunks.size
        )
    }
    
    /**
     * 计算纠错能力
     */
    fun calculateErrorCorrectionCapability(config: ErrorCorrectionConfig): CorrectionCapability {
        return CorrectionCapability(
            maxCorrectableErrors = config.parityShards / 2,
            maxCorrectableErasures = config.parityShards,
            redundancyPercentage = (config.parityShards.toDouble() / 
                (config.dataShards + config.parityShards).toDouble()) * 100.0
        )
    }
    
    data class ErrorCorrectionConfig(
        val dataShards: Int,
        val parityShards: Int
    )
    
    data class ErrorCorrectionResult(
        val originalData: ByteArray,
        val parityData: ByteArray,
        val config: ErrorCorrectionConfig,
        val totalSize: Int
    ) {
        fun getRedundancyPercentage(): Double {
            return (parityData.size.toDouble() / originalData.size.toDouble()) * 100.0
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as ErrorCorrectionResult
            
            if (!originalData.contentEquals(other.originalData)) return false
            if (!parityData.contentEquals(other.parityData)) return false
            if (config != other.config) return false
            if (totalSize != other.totalSize) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = originalData.contentHashCode()
            result = 31 * result + parityData.contentHashCode()
            result = 31 * result + config.hashCode()
            result = 31 * result + totalSize
            return result
        }
    }
    
    data class RecoveryResult(
        val recoveredData: ByteArray,
        val successRate: Double,
        val recoveredChunks: Int,
        val totalChunks: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as RecoveryResult
            
            if (!recoveredData.contentEquals(other.recoveredData)) return false
            if (successRate != other.successRate) return false
            if (recoveredChunks != other.recoveredChunks) return false
            if (totalChunks != other.totalChunks) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = recoveredData.contentHashCode()
            result = 31 * result + successRate.hashCode()
            result = 31 * result + recoveredChunks
            result = 31 * result + totalChunks
            return result
        }
    }
    
    data class CorrectionCapability(
        val maxCorrectableErrors: Int,
        val maxCorrectableErasures: Int,
        val redundancyPercentage: Double
    )
}
