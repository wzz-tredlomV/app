import kotlin.math.ceil
import kotlin.math.minOf
package com.qrfiletransfer.sender

import android.content.Context
import com.qrfiletransfer.model.FileChunk
import com.qrfiletransfer.model.QRConfig
import com.qrfiletransfer.model.TransferSession
import com.qrfiletransfer.utils.BinaryUtils
import com.qrfiletransfer.utils.HashUtils
import com.qrfiletransfer.utils.QRVersionCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件编码器
 * 负责将文件分块、压缩、添加纠错码，并生成二维码数据
 */
class FileEncoder(private val context: Context) {
    
    companion object {
        private const val TAG = "FileEncoder"
        private const val METADATA_SIZE = 200  // 元数据预留空间
    }
    
    private val compressionManager = CompressionManager(context)
    private val errorCorrection = ErrorCorrection()
    
    /**
     * 编码文件为可传输的块
     */
    suspend fun encodeFile(
        file: File,
        config: QRConfig,
        sessionId: String
    ): EncodingResult = withContext(Dispatchers.IO) {
        try {
            // 1. 读取文件
            val fileData = file.readBytes()
            val fileHash = HashUtils.sha256(fileData)
            val fileId = HashUtils.createFileId(file.path, file.length())
            
            // 2. 计算最佳配置
            val qrConfig = QRVersionCalculator.calculateOptimalConfig(
                dataSize = fileData.size,
                maxQRSize = 800,
                minVersion = config.minVersion,
                maxVersion = config.maxVersion,
                preferredErrorLevel = config.errorCorrectionLevel
            )
            
            // 3. 决定是否压缩
            val shouldCompress = config.compressionAlgorithm != "NONE" && 
                file.length() > config.compressionThreshold &&
                qrConfig.compressionRecommended
            
            val processedData = if (shouldCompress) {
                val compressionResult = compressionManager.autoCompress(fileData)
                ProcessedData(
                    data = compressionResult.compressedData,
                    compressionRatio = compressionResult.compressionRatio,
                    compressionAlgorithm = compressionResult.algorithm
                )
            } else {
                ProcessedData(
                    data = fileData,
                    compressionRatio = 1.0,
                    compressionAlgorithm = "NONE"
                )
            }
            
            // 4. 添加纠错码
            val errorCorrectionResult = if (config.errorCorrectionEnabled) {
                val ecConfig = ErrorCorrection.PRESET_CONFIGS["MEDIUM"]!!
                errorCorrection.addErrorCorrection(processedData.data, ecConfig)
            } else {
                ErrorCorrection.ErrorCorrectionResult(
                    originalData = processedData.data,
                    parityData = ByteArray(0),
                    config = ErrorCorrection.ErrorCorrectionConfig(10, 0),
                    totalSize = processedData.data.size
                )
            }
            
            // 5. 分块
            val chunkSize = calculateOptimalChunkSize(
                qrConfig.dataCapacity,
                processedData.compressionAlgorithm,
                config.errorCorrectionEnabled
            )
            
            val combinedData = if (config.errorCorrectionEnabled) {
                // 将数据和校验数据合并
                ByteArray(errorCorrectionResult.originalData.size + errorCorrectionResult.parityData.size).apply {
                    System.arraycopy(
                        errorCorrectionResult.originalData, 0, 
                        this, 0, errorCorrectionResult.originalData.size
                    )
                    System.arraycopy(
                        errorCorrectionResult.parityData, 0,
                        this, errorCorrectionResult.originalData.size,
                        errorCorrectionResult.parityData.size
                    )
                }
            } else {
                errorCorrectionResult.originalData
            }
            
            val chunks = splitIntoChunks(
                combinedData,
                chunkSize,
                fileId,
                sessionId,
                fileHash,
                file.name,
                file.length(),
                qrConfig.version,
                qrConfig.errorCorrectionLevel,
                processedData.compressionAlgorithm,
                processedData.compressionRatio,
                config.errorCorrectionEnabled,
                errorCorrectionResult.parityData
            )
            
            // 6. 创建传输会话
            val session = TransferSession(
                id = sessionId,
                fileId = fileId,
                fileName = file.name,
                fileSize = file.length(),
                fileHash = fileHash,
                totalChunks = chunks.size,
                compressionEnabled = shouldCompress,
                errorCorrectionEnabled = config.errorCorrectionEnabled,
                qrVersion = qrConfig.version,
                resumeToken = HashUtils.sha256("${sessionId}:${System.currentTimeMillis()}")
            )
            
            EncodingResult.Success(
                chunks = chunks,
                session = session,
                qrConfig = qrConfig,
                compressionInfo = processedData,
                errorCorrectionInfo = if (config.errorCorrectionEnabled) errorCorrectionResult else null
            )
            
        } catch (e: Exception) {
            EncodingResult.Error(
                errorMessage = "文件编码失败: ${e.message}",
                exception = e
            )
        }
    }
    
    /**
     * 从指定位置恢复编码
     */
    suspend fun resumeEncoding(
        session: TransferSession,
        lastChunkIndex: Int
    ): ResumeResult = withContext(Dispatchers.IO) {
        try {
            // 这里可以实现断点续传的逻辑
            // 读取已存在的块，从指定位置继续
            ResumeResult.Success(
                startIndex = lastChunkIndex + 1,
                estimatedTime = calculateRemainingTime(session, lastChunkIndex)
            )
        } catch (e: Exception) {
            ResumeResult.Error(e.message ?: "恢复失败")
        }
    }
    
    /**
     * 计算最佳分块大小
     */
    private fun calculateOptimalChunkSize(
        qrCapacity: Int,
        compressionAlgorithm: String,
        errorCorrectionEnabled: Boolean
    ): Int {
        // 预留空间给元数据和可能的开销
        var availableSize = qrCapacity - METADATA_SIZE
        
        if (errorCorrectionEnabled) {
            // 为纠错信息预留空间
            availableSize = (availableSize * 0.7).toInt()
        }
        
        // 根据压缩算法调整
        availableSize = when (compressionAlgorithm) {
            "LZ4" -> (availableSize * 1.1).toInt()  // LZ4压缩后可能更小
            "DEFLATE" -> (availableSize * 1.05).toInt()
            else -> availableSize
        }
        
        return minOf(availableSize, 2000)  // 最大2000字节
    }
    
    /**
     * 将数据分块
     */
    private fun splitIntoChunks(
        data: ByteArray,
        chunkSize: Int,
        fileId: String,
        sessionId: String,
        fileHash: String,
        fileName: String,
        fileSize: Long,
        qrVersion: Int,
        errorCorrectionLevel: String,
        compressionAlgorithm: String,
        compressionRatio: Double,
        errorCorrectionEnabled: Boolean,
        parityData: ByteArray
    ): List<FileChunk> {
        val chunks = mutableListOf<FileChunk>()
        val totalChunks = ceil(data.size.toDouble() / chunkSize.toDouble()).toInt()
        
        for (i in 0 until totalChunks) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, data.size)
            val chunkData = data.copyOfRange(start, end)
            
            // 计算当前块的哈希
            val chunkHash = HashUtils.sha256(chunkData)
            
            // 如果有纠错数据，添加校验信息
            val parityBytes = if (errorCorrectionEnabled && parityData.isNotEmpty()) {
                // 计算当前块对应的校验数据
                val parityStart = i * (parityData.size / totalChunks)
                val parityEnd = minOf(parityStart + (parityData.size / totalChunks), parityData.size)
                val parityChunk = parityData.copyOfRange(parityStart, parityEnd)
                BinaryUtils.bytesToBase64(parityChunk)
            } else {
                null
            }
            
            val chunk = FileChunk(
                fileId = fileId,
                sessionId = sessionId,
                chunkIndex = i,
                totalChunks = totalChunks,
                data = BinaryUtils.bytesToBase64(chunkData),
                dataHash = chunkHash,
                fileHash = fileHash,
                qrVersion = qrVersion,
                errorCorrectionLevel = errorCorrectionLevel,
                parityBytes = parityBytes,
                isCompressed = compressionAlgorithm != "NONE",
                compressionRatio = compressionRatio,
                fileName = fileName,
                fileSize = fileSize
            )
            
            chunks.add(chunk)
        }
        
        return chunks
    }
    
    /**
     * 计算剩余时间
     */
    private fun calculateRemainingTime(session: TransferSession, lastChunkIndex: Int): Long {
        val remainingChunks = session.totalChunks - lastChunkIndex - 1
        val averageTimePerChunk = 1000L  // 假设每个块1秒
        
        return remainingChunks * averageTimePerChunk
    }
    
    sealed class EncodingResult {
        data class Success(
            val chunks: List<FileChunk>,
            val session: TransferSession,
            val qrConfig: QRVersionCalculator.QRConfigResult,
            val compressionInfo: ProcessedData,
            val errorCorrectionInfo: ErrorCorrection.ErrorCorrectionResult?
        ) : EncodingResult()
        
        data class Error(
            val errorMessage: String,
            val exception: Exception? = null
        ) : EncodingResult()
    }
    
    sealed class ResumeResult {
        data class Success(
            val startIndex: Int,
            val estimatedTime: Long
        ) : ResumeResult()
        
        data class Error(val message: String) : ResumeResult()
    }
    
    data class ProcessedData(
        val data: ByteArray,
        val compressionRatio: Double,
        val compressionAlgorithm: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as ProcessedData
            
            if (!data.contentEquals(other.data)) return false
            if (compressionRatio != other.compressionRatio) return false
            if (compressionAlgorithm != other.compressionAlgorithm) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + compressionRatio.hashCode()
            result = 31 * result + compressionAlgorithm.hashCode()
            return result
        }
    }
    
    // 导入必要的数学函数
    import kotlin.math.ceil
    import kotlin.math.minOf
}
