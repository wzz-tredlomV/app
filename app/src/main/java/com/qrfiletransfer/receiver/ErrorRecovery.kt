import kotlin.math.log
import kotlin.math.min
package com.qrfiletransfer.receiver

import com.qrfiletransfer.sender.ErrorCorrection
import com.qrfiletransfer.utils.BinaryUtils
import com.qrfiletransfer.utils.HashUtils

/**
 * 错误恢复管理器
 * 处理接收到的损坏数据块
 */
class ErrorRecovery {
    
    private val errorCorrection = ErrorCorrection()
    
    /**
     * 恢复损坏的块
     */
    fun recoverDamagedChunks(
        data: ByteArray,
        parityData: ByteArray,
        damagedIndices: List<Int>,
        chunkSize: Int
    ): RecoveryResult {
        try {
            val dataShards = 10
            val parityShards = parityData.size / (data.size / dataShards / chunkSize)
            
            val config = ErrorCorrection.ErrorCorrectionConfig(dataShards, parityShards)
            
            val recoveryResult = errorCorrection.recoverData(
                data = data,
                parityData = parityData,
                config = config,
                damagedIndices = damagedIndices
            )
            
            return RecoveryResult(
                recoveredData = recoveryResult.recoveredData,
                successRate = recoveryResult.successRate,
                recoveredChunks = recoveryResult.recoveredChunks,
                totalChunks = recoveryResult.totalChunks,
                originalSize = data.size
            )
            
        } catch (e: Exception) {
            return RecoveryResult(
                recoveredData = data,
                successRate = 0.0,
                recoveredChunks = 0,
                totalChunks = damagedIndices.size,
                originalSize = data.size,
                error = e.message
            )
        }
    }
    
    /**
     * 验证数据完整性
     */
    fun verifyDataIntegrity(data: ByteArray, expectedHash: String): VerificationResult {
        val actualHash = HashUtils.sha256(data)
        val matches = actualHash == expectedHash
        
        return VerificationResult(
            isValid = matches,
            actualHash = actualHash,
            expectedHash = expectedHash
        )
    }
    
    /**
     * 计算数据损坏程度
     */
    fun calculateCorruptionLevel(data: ByteArray, expectedPattern: ByteArray? = null): Double {
        if (expectedPattern == null) {
            return calculateEntropyDeviation(data)
        }
        
        if (data.size != expectedPattern.size) {
            return 1.0
        }
        
        var differences = 0
        for (i in data.indices) {
            if (data[i] != expectedPattern[i]) {
                differences++
            }
        }
        
        return differences.toDouble() / data.size.toDouble()
    }
    
    /**
     * 通过熵值计算损坏程度
     */
    private fun calculateEntropyDeviation(data: ByteArray): Double {
        val distribution = IntArray(256)
        for (byte in data) {
            distribution[byte.toInt() and 0xFF]++
        }
        
        var entropy = 0.0
        val size = data.size.toDouble()
        
        for (count in distribution) {
            if (count > 0) {
                val probability = count / size
                entropy -= probability * (Math.log(probability) / Math.log(2.0))
            }
        }
        
        val maxEntropy = 8.0
        return (maxEntropy - entropy) / maxEntropy
    }
    
    /**
     * 尝试多种恢复方法
     */
    fun tryMultipleRecoveryMethods(
        data: ByteArray,
        parityData: ByteArray,
        damagedIndices: List<Int>
    ): MultiRecoveryResult {
        val methods = listOf(
            RecoveryMethod.REED_SOLOMON,
            RecoveryMethod.INTERPOLATION,
            RecoveryMethod.PATTERN_MATCHING
        )
        
        val results = mutableListOf<RecoveryAttempt>()
        
        for (method in methods) {
            try {
                val result = attemptRecovery(data, parityData, damagedIndices, method)
                results.add(result)
            } catch (e: Exception) {
                results.add(
                    RecoveryAttempt(
                        method = method,
                        successRate = 0.0,
                        recoveredData = data,
                        error = e.message
                    )
                )
            }
        }
        
        val bestResult = results.maxByOrNull { it.successRate }
        
        return MultiRecoveryResult(
            attempts = results,
            bestResult = bestResult,
            selectedMethod = bestResult?.method
        )
    }
    
    private fun attemptRecovery(
        data: ByteArray,
        parityData: ByteArray,
        damagedIndices: List<Int>,
        method: RecoveryMethod
    ): RecoveryAttempt {
        return when (method) {
            RecoveryMethod.REED_SOLOMON -> {
                val result = recoverDamagedChunks(data, parityData, damagedIndices, 255)
                RecoveryAttempt(
                    method = method,
                    successRate = result.successRate,
                    recoveredData = result.recoveredData,
                    error = result.error
                )
            }
            
            RecoveryMethod.INTERPOLATION -> {
                val recovered = interpolateData(data, damagedIndices)
                val successRate = estimateSuccessRate(data, recovered)
                
                RecoveryAttempt(
                    method = method,
                    successRate = successRate,
                    recoveredData = recovered
                )
            }
            
            RecoveryMethod.PATTERN_MATCHING -> {
                val recovered = patternMatchRecovery(data, damagedIndices)
                val successRate = estimateSuccessRate(data, recovered)
                
                RecoveryAttempt(
                    method = method,
                    successRate = successRate,
                    recoveredData = recovered
                )
            }
        }
    }
    
    private fun interpolateData(data: ByteArray, damagedIndices: List<Int>): ByteArray {
        val recovered = data.copyOf()
        
        for (index in damagedIndices.sorted()) {
            var prevIndex = index - 1
            var nextIndex = index + 1
            
            while (prevIndex >= 0 && damagedIndices.contains(prevIndex)) {
                prevIndex--
            }
            
            while (nextIndex < data.size && damagedIndices.contains(nextIndex)) {
                nextIndex++
            }
            
            if (prevIndex >= 0 && nextIndex < data.size) {
                val prevValue = data[prevIndex].toDouble() and 0xFF
                val nextValue = data[nextIndex].toDouble() and 0xFF
                val weight = (index - prevIndex).toDouble() / (nextIndex - prevIndex).toDouble()
                
                val interpolated = (prevValue * (1 - weight) + nextValue * weight).toInt()
                recovered[index] = interpolated.toByte()
            } else if (prevIndex >= 0) {
                recovered[index] = data[prevIndex]
            } else if (nextIndex < data.size) {
                recovered[index] = data[nextIndex]
            }
        }
        
        return recovered
    }
    
    private fun patternMatchRecovery(data: ByteArray, damagedIndices: List<Int>): ByteArray {
        val recovered = data.copyOf()
        
        val patternLength = findPatternLength(data)
        if (patternLength > 0) {
            for (index in damagedIndices) {
                val patternIndex = index % patternLength
                var candidate = -1
                var maxCount = 0
                
                val counts = mutableMapOf<Int, Int>()
                for (i in patternIndex until data.size step patternLength) {
                    if (!damagedIndices.contains(i)) {
                        val value = data[i].toInt() and 0xFF
                        counts[value] = counts.getOrDefault(value, 0) + 1
                        
                        if (counts[value]!! > maxCount) {
                            maxCount = counts[value]!!
                            candidate = value
                        }
                    }
                }
                
                if (candidate != -1) {
                    recovered[index] = candidate.toByte()
                }
            }
        }
        
        return recovered
    }
    
    private fun findPatternLength(data: ByteArray): Int {
        val maxPatternLength = minOf(100, data.size / 2)
        
        for (length in 1..maxPatternLength) {
            if (isPatternRepeating(data, length)) {
                return length
            }
        }
        
        return -1
    }
    
    private fun isPatternRepeating(data: ByteArray, patternLength: Int): Boolean {
        if (data.size < patternLength * 2) return false
        
        for (i in 0 until patternLength) {
            for (j in 1 until data.size / patternLength) {
                val index1 = i
                val index2 = j * patternLength + i
                
                if (index2 >= data.size) break
                
                if (data[index1] != data[index2]) {
                    return false
                }
            }
        }
        
        return true
    }
    
    private fun estimateSuccessRate(original: ByteArray, recovered: ByteArray): Double {
        if (original.size != recovered.size) return 0.0
        
        var matches = 0
        for (i in original.indices) {
            if (original[i] == recovered[i]) {
                matches++
            }
        }
        
        return matches.toDouble() / original.size.toDouble()
    }
    
    data class RecoveryResult(
        val recoveredData: ByteArray,
        val successRate: Double,
        val recoveredChunks: Int,
        val totalChunks: Int,
        val originalSize: Int,
        val error: String? = null
    )
    
    data class VerificationResult(
        val isValid: Boolean,
        val actualHash: String,
        val expectedHash: String
    )
    
    data class MultiRecoveryResult(
        val attempts: List<RecoveryAttempt>,
        val bestResult: RecoveryAttempt?,
        val selectedMethod: RecoveryMethod?
    )
    
    data class RecoveryAttempt(
        val method: RecoveryMethod,
        val successRate: Double,
        val recoveredData: ByteArray,
        val error: String? = null
    )
    
    enum class RecoveryMethod {
        REED_SOLOMON,
        INTERPOLATION,
        PATTERN_MATCHING
    }
    
    // 导入必要的数学函数
    import kotlin.math.log
    import kotlin.math.min
}
