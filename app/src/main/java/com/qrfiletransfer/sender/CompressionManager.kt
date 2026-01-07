package com.qrfiletransfer.sender

import android.content.Context
import com.qrfiletransfer.utils.BinaryUtils
import net.jpountz.lz4.LZ4Compressor
import net.jpountz.lz4.LZ4Factory
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterOutputStream

/**
 * 压缩管理器，支持多种压缩算法
 */
class CompressionManager(context: Context) {
    
    companion object {
        const val ALGORITHM_NONE = "NONE"
        const val ALGORITHM_DEFLATE = "DEFLATE"
        const val ALGORITHM_LZ4 = "LZ4"
        const val ALGORITHM_GZIP = "GZIP"
    }
    
    private val lz4Factory = LZ4Factory.fastestInstance()
    
    /**
     * 压缩数据
     */
    fun compress(data: ByteArray, algorithm: String = ALGORITHM_LZ4): CompressionResult {
        return when (algorithm) {
            ALGORITHM_DEFLATE -> deflateCompress(data)
            ALGORITHM_LZ4 -> lz4Compress(data)
            ALGORITHM_GZIP -> gzipCompress(data)
            else -> CompressionResult(data, 1.0, ALGORITHM_NONE)
        }
    }
    
    /**
     * 解压数据
     */
    fun decompress(data: ByteArray, algorithm: String): ByteArray {
        return when (algorithm) {
            ALGORITHM_DEFLATE -> deflateDecompress(data)
            ALGORITHM_LZ4 -> lz4Decompress(data)
            ALGORITHM_GZIP -> gzipDecompress(data)
            else -> data
        }
    }
    
    /**
     * 使用DEFLATE算法压缩
     */
    private fun deflateCompress(data: ByteArray): CompressionResult {
        val outputStream = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val deflaterStream = DeflaterOutputStream(outputStream, deflater)
        
        try {
            deflaterStream.write(data)
            deflaterStream.finish()
            deflaterStream.close()
            
            val compressed = outputStream.toByteArray()
            val ratio = data.size.toDouble() / compressed.size.toDouble()
            
            return CompressionResult(compressed, ratio, ALGORITHM_DEFLATE)
        } finally {
            deflater.end()
        }
    }
    
    /**
     * 使用DEFLATE算法解压
     */
    private fun deflateDecompress(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val inflater = Inflater(true)
        val inflaterStream = InflaterOutputStream(outputStream, inflater)
        
        try {
            inflaterStream.write(data)
            inflaterStream.finish()
            inflaterStream.close()
            
            return outputStream.toByteArray()
        } finally {
            inflater.end()
        }
    }
    
    /**
     * 使用LZ4算法压缩
     */
    private fun lz4Compress(data: ByteArray): CompressionResult {
        val compressor: LZ4Compressor = lz4Factory.fastCompressor()
        val maxCompressedLength = compressor.maxCompressedLength(data.size)
        val compressed = ByteArray(maxCompressedLength)
        
        val compressedLength = compressor.compress(data, 0, data.size, compressed, 0, maxCompressedLength)
        val result = compressed.copyOf(compressedLength)
        val ratio = data.size.toDouble() / result.size.toDouble()
        
        return CompressionResult(result, ratio, ALGORITHM_LZ4)
    }
    
    /**
     * 使用LZ4算法解压
     */
    private fun lz4Decompress(data: ByteArray): ByteArray {
        val decompressor = lz4Factory.fastDecompressor()
        // 注意：实际应用中需要知道解压后的大小
        // 这里我们假设原始大小不超过压缩数据的10倍
        val maxDecompressedLength = data.size * 10
        val decompressed = ByteArray(maxDecompressedLength)
        
        val decompressedLength = decompressor.decompress(data, 0, decompressed, 0, maxDecompressedLength)
        
        return decompressed.copyOf(decompressedLength)
    }
    
    /**
     * 使用GZIP算法压缩（兼容性更好）
     */
    private fun gzipCompress(data: ByteArray): CompressionResult {
        val outputStream = ByteArrayOutputStream()
        val gzipStream = java.util.zip.GZIPOutputStream(outputStream)
        
        gzipStream.write(data)
        gzipStream.close()
        
        val compressed = outputStream.toByteArray()
        val ratio = data.size.toDouble() / compressed.size.toDouble()
        
        return CompressionResult(compressed, ratio, ALGORITHM_GZIP)
    }
    
    /**
     * 使用GZIP算法解压
     */
    private fun gzipDecompress(data: ByteArray): ByteArray {
        val inputStream = java.io.ByteArrayInputStream(data)
        val gzipStream = java.util.zip.GZIPInputStream(inputStream)
        val outputStream = ByteArrayOutputStream()
        
        val buffer = ByteArray(1024)
        var length: Int
        while (gzipStream.read(buffer).also { length = it } != -1) {
            outputStream.write(buffer, 0, length)
        }
        
        gzipStream.close()
        outputStream.close()
        
        return outputStream.toByteArray()
    }
    
    /**
     * 自动选择最佳压缩算法
     */
    fun autoCompress(data: ByteArray): CompressionResult {
        // 尝试多种算法，选择压缩率最高的
        val algorithms = listOf(ALGORITHM_LZ4, ALGORITHM_DEFLATE, ALGORITHM_GZIP)
        var bestResult = CompressionResult(data, 1.0, ALGORITHM_NONE)
        
        for (algorithm in algorithms) {
            try {
                val result = compress(data, algorithm)
                if (result.compressionRatio > bestResult.compressionRatio) {
                    bestResult = result
                }
            } catch (e: Exception) {
                // 算法失败，继续尝试下一个
                continue
            }
        }
        
        return bestResult
    }
    
    /**
     * 检测数据是否可压缩
     */
    fun isCompressible(data: ByteArray): Boolean {
        if (data.size < 100) return false  // 太小没必要压缩
        
        // 计算熵值
        val entropy = calculateEntropy(data)
        
        // 熵值低表示数据有规律，可压缩
        return entropy < 7.5
    }
    
    /**
     * 计算数据的熵值（用于判断可压缩性）
     */
    private fun calculateEntropy(data: ByteArray): Double {
        val frequency = IntArray(256)
        
        for (byte in data) {
            frequency[byte.toInt() and 0xFF]++
        }
        
        var entropy = 0.0
        val size = data.size.toDouble()
        
        for (count in frequency) {
            if (count > 0) {
                val probability = count / size
                entropy -= probability * (Math.log(probability) / Math.log(2.0))
            }
        }
        
        return entropy
    }
    
    data class CompressionResult(
        val compressedData: ByteArray,
        val compressionRatio: Double,
        val algorithm: String
    ) {
        fun getCompressionPercentage(): Int {
            return ((1.0 - 1.0 / compressionRatio) * 100).toInt()
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as CompressionResult
            
            if (!compressedData.contentEquals(other.compressedData)) return false
            if (compressionRatio != other.compressionRatio) return false
            if (algorithm != other.algorithm) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = compressedData.contentHashCode()
            result = 31 * result + compressionRatio.hashCode()
            result = 31 * result + algorithm.hashCode()
            return result
        }
    }
}
