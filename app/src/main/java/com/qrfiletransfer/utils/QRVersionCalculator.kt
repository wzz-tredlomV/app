package com.qrfiletransfer.utils

import com.google.zxing.qrcode.encoder.QRCode
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 自适应二维码版本计算器
 * 根据数据大小自动选择最佳的二维码版本和纠错级别
 */
object QRVersionCalculator {
    
    data class QRConfigResult(
        val version: Int,
        val errorCorrectionLevel: String,
        val dataCapacity: Int,
        val optimalChunkSize: Int,
        val estimatedQRSize: Int,
        val compressionRecommended: Boolean
    )
    
    private val capacityTable = mapOf(
        "L" to listOf(152, 272, 440, 640, 864, 1088, 1248, 1552, 1856, 2192),
        "M" to listOf(128, 224, 352, 512, 688, 864, 992, 1232, 1456, 1728),
        "Q" to listOf(104, 176, 272, 384, 496, 608, 704, 880, 1056, 1232),
        "H" to listOf(72, 128, 208, 288, 368, 480, 528, 688, 800, 976),
        
        "L11-20" to listOf(2592, 2960, 3424, 3688, 4184, 4712, 5176, 5768, 6360, 6888),
        "M11-20" to listOf(2032, 2320, 2672, 2920, 3320, 3624, 4056, 4504, 5016, 5352),
        "Q11-20" to listOf(1440, 1648, 1952, 2088, 2360, 2600, 2936, 3176, 3560, 3880),
        "H11-20" to listOf(1048, 1184, 1376, 1504, 1784, 2024, 2264, 2504, 2728, 3080),
        
        "L21-30" to listOf(7456, 8048, 8752, 9392, 10208, 10960, 11744, 12248, 13048, 13880),
        "M21-30" to listOf(5712, 6256, 6880, 7312, 8000, 8496, 9024, 9544, 10136, 10984),
        "Q21-30" to listOf(4096, 4544, 4912, 5312, 5744, 6032, 6464, 6968, 7288, 7880),
        "H21-30" to listOf(3248, 3536, 3712, 4112, 4304, 4768, 5024, 5288, 5608, 5960),
        
        "L31-40" to listOf(14744, 15640, 16568, 17528, 18448, 19472, 20528, 21616, 22496, 23648),
        "M31-40" to listOf(11640, 12328, 13048, 13800, 14496, 15312, 15936, 16816, 17728, 18672),
        "Q31-40" to listOf(8264, 8920, 9368, 9848, 10288, 10832, 11408, 12016, 12656, 13328),
        "H31-40" to listOf(6344, 6760, 7208, 7688, 7888, 8432, 8768, 9136, 9776, 10208)
    )
    
    /**
     * 计算最佳二维码配置
     */
    fun calculateOptimalConfig(
        dataSize: Int,
        maxQRSize: Int = 800,
        minVersion: Int = 1,
        maxVersion: Int = 40,
        preferredErrorLevel: String = "M"
    ): QRConfigResult {
        val compressedSize = estimateCompressedSize(dataSize)
        val shouldCompress = compressedSize < dataSize * 0.9
        
        val effectiveSize = if (shouldCompress) compressedSize else dataSize
        
        val chunkConfig = calculateChunkConfig(effectiveSize, preferredErrorLevel)
        
        val optimalVersion = findOptimalVersion(chunkConfig.chunkSize, minVersion, maxVersion)
        
        val qrPixelSize = calculateQRPixelSize(optimalVersion, maxQRSize)
        
        val errorLevel = if (chunkConfig.totalChunks > 10) {
            if (preferredErrorLevel == "L") "M" else preferredErrorLevel
        } else {
            preferredErrorLevel
        }
        
        val actualCapacity = getCapacity(optimalVersion, errorLevel)
        
        return QRConfigResult(
            version = optimalVersion,
            errorCorrectionLevel = errorLevel,
            dataCapacity = actualCapacity,
            optimalChunkSize = minOf(chunkConfig.chunkSize, actualCapacity - 100),
            estimatedQRSize = qrPixelSize,
            compressionRecommended = shouldCompress
        )
    }
    
    /**
     * 估算压缩后的大小
     */
    private fun estimateCompressedSize(originalSize: Int): Int {
        return when {
            originalSize < 1024 -> originalSize
            originalSize < 1024 * 1024 -> (originalSize * 0.7).toInt()
            else -> (originalSize * 0.6).toInt()
        }
    }
    
    /**
     * 计算分块配置
     */
    private fun calculateChunkConfig(
        dataSize: Int,
        errorLevel: String
    ): ChunkConfig {
        val baseChunkSize = when (errorLevel) {
            "H" -> 800
            "Q" -> 1200
            "M" -> 1600
            "L" -> 2000
            else -> 1500
        }
        
        val adjustedChunkSize = when {
            dataSize < 10 * 1024 -> 500
            dataSize < 100 * 1024 -> 1000
            dataSize < 1024 * 1024 -> 1500
            else -> 2000
        }
        
        val chunkSize = minOf(baseChunkSize, adjustedChunkSize)
        val totalChunks = ceil(dataSize.toDouble() / chunkSize).toInt()
        
        return ChunkConfig(chunkSize, totalChunks)
    }
    
    /**
     * 找到最佳二维码版本
     */
    private fun findOptimalVersion(
        chunkSize: Int,
        minVersion: Int,
        maxVersion: Int
    ): Int {
        for (version in minVersion..maxVersion) {
            for (errorLevel in listOf("L", "M", "Q", "H")) {
                val capacity = getCapacity(version, errorLevel)
                if (capacity >= chunkSize + 100) {
                    return version
                }
            }
        }
        
        return maxVersion
    }
    
    /**
     * 获取指定版本和纠错级别的容量
     */
    fun getCapacity(version: Int, errorLevel: String): Int {
        val index = version - 1
        
        return when {
            version <= 10 -> capacityTable[errorLevel]?.getOrNull(index) ?: 0
            version <= 20 -> capacityTable["${errorLevel}11-20"]?.getOrNull(index - 10) ?: 0
            version <= 30 -> capacityTable["${errorLevel}21-30"]?.getOrNull(index - 20) ?: 0
            version <= 40 -> capacityTable["${errorLevel}31-40"]?.getOrNull(index - 30) ?: 0
            else -> 0
        }
    }
    
    /**
     * 计算二维码像素大小
     */
    private fun calculateQRPixelSize(version: Int, maxSize: Int): Int {
        val modules = 21 + 4 * (version - 1)
        val moduleSize = maxSize / modules
        return modules * moduleSize
    }
    
    /**
     * 计算传输时间估计
     */
    fun estimateTransmissionTime(
        totalChunks: Int,
        qrDisplayTime: Long,
        scanningTime: Long
    ): Long {
        val perChunkTime = qrDisplayTime + scanningTime
        return totalChunks * perChunkTime
    }
    
    /**
     * 计算带宽效率
     */
    fun calculateBandwidthEfficiency(
        originalSize: Int,
        qrCount: Int,
        qrCapacity: Int
    ): Double {
        val totalCapacity = qrCount * qrCapacity
        return originalSize.toDouble() / totalCapacity
    }
    
    private data class ChunkConfig(
        val chunkSize: Int,
        val totalChunks: Int
    )
    
    /**
     * 动态调整分块大小
     */
    fun dynamicAdjustChunkSize(
        currentChunkSize: Int,
        successRate: Double,
        scanningSpeed: Long
    ): Int {
        return when {
            successRate > 0.95 && scanningSpeed < 1000 -> {
                minOf(currentChunkSize * 120 / 100, 2953)
            }
            successRate < 0.8 || scanningSpeed > 3000 -> {
                maxOf(currentChunkSize * 80 / 100, 500)
            }
            else -> currentChunkSize
        }
    }
    
    /**
     * 获取所有可用的二维码版本
     */
    fun getAvailableVersions(): List<Int> {
        return (1..40).toList()
    }
    
    /**
     * 获取所有纠错级别
     */
    fun getErrorCorrectionLevels(): List<String> {
        return listOf("L", "M", "Q", "H")
    }
    
    /**
     * 获取建议的配置
     */
    fun getSuggestedConfigs(): Map<String, QRConfigResult> {
        return mapOf(
            "小型文件" to calculateOptimalConfig(10 * 1024),
            "中型文件" to calculateOptimalConfig(100 * 1024),
            "大型文件" to calculateOptimalConfig(1024 * 1024)
        )
    }
}
