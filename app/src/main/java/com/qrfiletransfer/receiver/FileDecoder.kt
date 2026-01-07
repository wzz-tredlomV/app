package com.qrfiletransfer.receiver

import android.content.Context
import com.qrfiletransfer.model.FileChunk
import com.qrfiletransfer.model.TransferSession
import com.qrfiletransfer.sender.CompressionManager
import com.qrfiletransfer.utils.BinaryUtils
import com.qrfiletransfer.utils.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 文件解码器
 * 负责验证、重组和解码接收到的文件块
 */
class FileDecoder(private val context: Context) {
    
    companion object {
        private const val TAG = "FileDecoder"
    }
    
    private val compressionManager = CompressionManager(context)
    private val errorRecovery = ErrorRecovery()
    
    private val receivedChunks = mutableMapOf<String, MutableList<FileChunk>>()
    private val chunkDataMap = mutableMapOf<String, MutableMap<Int, ByteArray>>()
    private val sessions = mutableMapOf<String, TransferSession>()
    
    /**
     * 处理接收到的块
     */
    suspend fun processChunk(chunk: FileChunk): ProcessResult = withContext(Dispatchers.IO) {
        try {
            val validationResult = validateChunk(chunk)
            if (!validationResult.isValid) {
                return@withContext ProcessResult.Error(
                    "块验证失败: ${validationResult.errorMessage}",
                    chunk.chunkIndex
                )
            }
            
            val fileId = chunk.fileId
            val sessionId = chunk.sessionId
            
            if (!receivedChunks.containsKey(fileId)) {
                receivedChunks[fileId] = mutableListOf()
                chunkDataMap[fileId] = mutableMapOf()
                
                sessions[sessionId] = TransferSession(
                    id = sessionId,
                    fileId = fileId,
                    fileName = chunk.fileName,
                    fileSize = chunk.fileSize,
                    fileHash = chunk.fileHash,
                    totalChunks = chunk.totalChunks
                )
            }
            
            if (chunkDataMap[fileId]?.containsKey(chunk.chunkIndex) == true) {
                return@withContext ProcessResult.Duplicate(chunk.chunkIndex)
            }
            
            val chunkData = BinaryUtils.base64ToBytes(chunk.data)
            chunkDataMap[fileId]?.set(chunk.chunkIndex, chunkData)
            receivedChunks[fileId]?.add(chunk)
            
            sessions[sessionId]?.let { session ->
                sessions[sessionId] = session.copy(
                    chunksReceived = session.chunksReceived + 1
                )
            }
            
            val received = receivedChunks[fileId] ?: emptyList()
            if (received.size == chunk.totalChunks) {
                return@withContext assembleFile(fileId, sessionId)
            }
            
            return@withContext ProcessResult.Progress(
                received.size,
                chunk.totalChunks,
                chunk.chunkIndex
            )
            
        } catch (e: Exception) {
            return@withContext ProcessResult.Error(
                "处理块时出错: ${e.message}",
                chunk.chunkIndex,
                e
            )
        }
    }
    
    /**
     * 验证块的完整性
     */
    private fun validateChunk(chunk: FileChunk): ValidationResult {
        val chunkData = BinaryUtils.base64ToBytes(chunk.data)
        val calculatedHash = HashUtils.sha256(chunkData)
        
        if (calculatedHash != chunk.dataHash) {
            return ValidationResult(false, "数据哈希不匹配")
        }
        
        if (chunk.chunkIndex < 0 || chunk.chunkIndex >= chunk.totalChunks) {
            return ValidationResult(false, "索引超出范围")
        }
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - chunk.timestamp > 30 * 60 * 1000) {
            return ValidationResult(false, "块已过期")
        }
        
        return ValidationResult(true)
    }
    
    /**
     * 组装文件
     */
    private suspend fun assembleFile(fileId: String, sessionId: String): ProcessResult {
        try {
            val session = sessions[sessionId] ?: return ProcessResult.Error("会话不存在")
            val chunkMap = chunkDataMap[fileId] ?: return ProcessResult.Error("块数据不存在")
            
            val sortedIndices = chunkMap.keys.sorted()
            val chunks = sortedIndices.map { chunkMap[it]!! }
            
            val combinedData = BinaryUtils.mergeChunks(chunks)
            
            val sessionChunks = receivedChunks[fileId] ?: emptyList()
            val firstChunk = sessionChunks.firstOrNull() ?: return ProcessResult.Error("无块数据")
            
            val (data, parityData) = if (firstChunk.parityBytes != null) {
                val dataSize = combinedData.size * 
                    firstChunk.totalChunks / (firstChunk.totalChunks + 1)
                val data = combinedData.copyOfRange(0, dataSize)
                val parity = combinedData.copyOfRange(dataSize, combinedData.size)
                Pair(data, parity)
            } else {
                Pair(combinedData, ByteArray(0))
            }
            
            val recoveredData = if (parityData.isNotEmpty()) {
                val damagedIndices = findDamagedChunks(sessionChunks)
                if (damagedIndices.isNotEmpty()) {
                    val recoveryResult = errorRecovery.recoverDamagedChunks(
                        data = data,
                        parityData = parityData,
                        damagedIndices = damagedIndices,
                        chunkSize = data.size / firstChunk.totalChunks
                    )
                    
                    if (recoveryResult.successRate < 0.8) {
                        return ProcessResult.Error("数据损坏严重，无法恢复")
                    }
                    
                    recoveryResult.recoveredData
                } else {
                    data
                }
            } else {
                data
            }
            
            val decompressedData = if (firstChunk.isCompressed) {
                compressionManager.decompress(
                    recoveredData,
                    firstChunk.compressionRatio?.let { if (it > 2.0) "LZ4" else "DEFLATE" } ?: "DEFLATE"
                )
            } else {
                recoveredData
            }
            
            val fileHash = HashUtils.sha256(decompressedData)
            if (fileHash != session.fileHash) {
                return ProcessResult.Error("文件哈希不匹配，文件可能损坏")
            }
            
            val outputFile = saveFile(decompressedData, session.fileName)
            
            cleanupSession(fileId)
            
            return ProcessResult.Success(
                file = outputFile,
                fileSize = decompressedData.size.toLong(),
                fileHash = fileHash,
                chunksProcessed = sessionChunks.size,
                compressionUsed = firstChunk.isCompressed,
                errorRecoveryUsed = parityData.isNotEmpty()
            )
            
        } catch (e: Exception) {
            return ProcessResult.Error("组装文件失败: ${e.message}", exception = e)
        }
    }
    
    /**
     * 查找损坏的块
     */
    private fun findDamagedChunks(chunks: List<FileChunk>): List<Int> {
        val damaged = mutableListOf<Int>()
        
        for (chunk in chunks) {
            val chunkData = BinaryUtils.base64ToBytes(chunk.data)
            val calculatedHash = HashUtils.sha256(chunkData)
            
            if (calculatedHash != chunk.dataHash) {
                damaged.add(chunk.chunkIndex)
            }
        }
        
        return damaged
    }
    
    /**
     * 保存文件
     */
    private fun saveFile(data: ByteArray, fileName: String): File {
        val outputDir = File(context.getExternalFilesDir(null), "received")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        
        val outputFile = File(outputDir, fileName)
        var counter = 1
        
        var finalFile = outputFile
        while (finalFile.exists()) {
            val nameWithoutExt = fileName.substringBeforeLast(".")
            val ext = fileName.substringAfterLast(".", "")
            val newName = if (ext.isNotEmpty()) {
                "${nameWithoutExt}_$counter.$ext"
            } else {
                "${fileName}_$counter"
            }
            finalFile = File(outputDir, newName)
            counter++
        }
        
        FileOutputStream(finalFile).use { fos ->
            fos.write(data)
        }
        
        return finalFile
    }
    
    /**
     * 清理会话数据
     */
    private fun cleanupSession(fileId: String) {
        receivedChunks.remove(fileId)
        chunkDataMap.remove(fileId)
        
        val sessionToRemove = sessions.values.find { it.fileId == fileId }
        sessionToRemove?.let { sessions.remove(it.id) }
    }
    
    /**
     * 获取会话信息
     */
    fun getSession(sessionId: String): TransferSession? {
        return sessions[sessionId]
    }
    
    /**
     * 获取接收进度
     */
    fun getProgress(fileId: String): Pair<Int, Int>? {
        val chunks = receivedChunks[fileId] ?: return null
        val total = chunks.firstOrNull()?.totalChunks ?: 0
        
        return Pair(chunks.size, total)
    }
    
    /**
     * 恢复传输（断点续传）
     */
    suspend fun resumeTransfer(sessionId: String, resumeToken: String): ResumeResult {
        return withContext(Dispatchers.IO) {
            ResumeResult.Success(
                sessionId = sessionId,
                resumeToken = resumeToken,
                chunksReceived = receivedChunks.values.sumOf { it.size }
            )
        }
    }
    
    sealed class ProcessResult {
        data class Progress(
            val received: Int,
            val total: Int,
            val lastIndex: Int
        ) : ProcessResult()
        
        data class Success(
            val file: File,
            val fileSize: Long,
            val fileHash: String,
            val chunksProcessed: Int,
            val compressionUsed: Boolean,
            val errorRecoveryUsed: Boolean
        ) : ProcessResult()
        
        data class Error(
            val message: String,
            val chunkIndex: Int? = null,
            val exception: Exception? = null
        ) : ProcessResult()
        
        data class Duplicate(val chunkIndex: Int) : ProcessResult()
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
    
    sealed class ResumeResult {
        data class Success(
            val sessionId: String,
            val resumeToken: String,
            val chunksReceived: Int
        ) : ResumeResult()
        
        data class Error(val message: String) : ResumeResult()
    }
}
