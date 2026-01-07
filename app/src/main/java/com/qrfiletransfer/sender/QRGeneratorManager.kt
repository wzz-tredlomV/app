package com.qrfiletransfer.sender

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qrfiletransfer.model.FileChunk
import com.qrfiletransfer.utils.QRVersionCalculator
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * 二维码生成管理器
 * 负责生成和显示二维码序列，支持自适应版本和断点续传
 */
class QRGeneratorManager(
    private val context: Context,
    private val lifecycle: Lifecycle
) : LifecycleObserver, CoroutineScope {
    
    companion object {
        private const val TAG = "QRGeneratorManager"
        private const val MIN_DISPLAY_TIME = 300L
        private const val MAX_DISPLAY_TIME = 2000L
        private const val DEFAULT_DISPLAY_TIME = 500L
    }
    
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Main + job
    
    private val qrCodeWriter = QRCodeWriter()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 状态
    private var currentIndex = 0
    private var isRunning = false
    private var isPaused = false
    private var displayTime = DEFAULT_DISPLAY_TIME
    private var successCount = 0
    private var totalScanned = 0
    private var scanningSpeed = mutableListOf<Long>()
    
    // 数据
    private var chunks: List<FileChunk> = emptyList()
    private var currentSessionId: String? = null
    
    // 回调
    private var onQRGenerated: ((Bitmap, FileChunk, Int) -> Unit)? = null
    private var onProgressUpdate: ((Int, Int, Long) -> Unit)? = null
    private var onCompletion: (() -> Unit)? = null
    private var onError: ((Exception) -> Unit)? = null
    
    init {
        lifecycle.addObserver(this)
    }
    
    /**
     * 开始生成并显示二维码序列
     */
    fun startGeneration(
        chunks: List<FileChunk>,
        sessionId: String,
        initialIndex: Int = 0,
        adaptiveSpeed: Boolean = true
    ) {
        if (isRunning) {
            stopGeneration()
        }
        
        this.chunks = chunks
        this.currentSessionId = sessionId
        this.currentIndex = initialIndex
        this.isRunning = true
        this.isPaused = false
        
        launch {
            generateSequence(adaptiveSpeed)
        }
    }
    
    /**
     * 暂停生成
     */
    fun pauseGeneration() {
        isPaused = true
    }
    
    /**
     * 恢复生成
     */
    fun resumeGeneration() {
        if (isRunning && isPaused) {
            isPaused = false
            launch {
                generateSequence(true)
            }
        }
    }
    
    /**
     * 停止生成
     */
    fun stopGeneration() {
        isRunning = false
        isPaused = false
        job.cancelChildren()
    }
    
    /**
     * 跳转到指定块
     */
    fun jumpToChunk(index: Int) {
        if (index in 0 until chunks.size) {
            currentIndex = index
        }
    }
    
    /**
     * 更新扫描统计
     */
    fun updateScanStatistics(success: Boolean, scanTime: Long) {
        totalScanned++
        if (success) {
            successCount++
        }
        
        scanningSpeed.add(scanTime)
        
        if (scanningSpeed.size > 20) {
            scanningSpeed.removeAt(0)
        }
        
        if (scanningSpeed.isNotEmpty()) {
            val averageScanTime = scanningSpeed.average().toLong()
            displayTime = when {
                averageScanTime < 300 -> maxOf(averageScanTime * 2, MIN_DISPLAY_TIME)
                averageScanTime > 1000 -> minOf(averageScanTime, MAX_DISPLAY_TIME)
                else -> DEFAULT_DISPLAY_TIME
            }
        }
    }
    
    /**
     * 获取当前成功率
     */
    fun getSuccessRate(): Double {
        return if (totalScanned > 0) {
            successCount.toDouble() / totalScanned.toDouble()
        } else {
            1.0
        }
    }
    
    /**
     * 获取平均扫描时间
     */
    fun getAverageScanTime(): Long {
        return if (scanningSpeed.isNotEmpty()) {
            scanningSpeed.average().toLong()
        } else {
            DEFAULT_DISPLAY_TIME
        }
    }
    
    /**
     * 生成二维码序列
     */
    private suspend fun generateSequence(adaptiveSpeed: Boolean) = withContext(Dispatchers.IO) {
        while (isRunning && currentIndex < chunks.size) {
            if (isPaused) {
                delay(100)
                continue
            }
            
            try {
                val chunk = chunks[currentIndex]
                val bitmap = generateQRCode(chunk)
                
                withContext(Dispatchers.Main) {
                    onQRGenerated?.invoke(bitmap, chunk, currentIndex)
                    onProgressUpdate?.invoke(currentIndex + 1, chunks.size, displayTime)
                }
                
                val waitTime = if (adaptiveSpeed) {
                    calculateAdaptiveDisplayTime(chunk, getSuccessRate())
                } else {
                    displayTime
                }
                
                delay(waitTime)
                
                currentIndex++
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError?.invoke(e)
                }
                break
            }
        }
        
        if (currentIndex >= chunks.size && isRunning) {
            withContext(Dispatchers.Main) {
                onCompletion?.invoke()
            }
            isRunning = false
        }
    }
    
    /**
     * 生成单个二维码
     */
    private fun generateQRCode(chunk: FileChunk): Bitmap {
        val jsonData = chunk.toJson()
        
        val version = chunk.qrVersion
        val errorLevel = ErrorCorrectionLevel.valueOf(chunk.errorCorrectionLevel)
        
        val hints = mutableMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.ERROR_CORRECTION, errorLevel)
            put(EncodeHintType.QR_VERSION, version)
            put(EncodeHintType.MARGIN, 1)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        
        val bitMatrix: BitMatrix = qrCodeWriter.encode(
            jsonData,
            BarcodeFormat.QR_CODE,
            version * 4 + 17,
            version * 4 + 17,
            hints
        )
        
        return createBitmapFromMatrix(bitMatrix)
    }
    
    /**
     * 从BitMatrix创建Bitmap
     */
    private fun createBitmapFromMatrix(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }
    
    /**
     * 计算自适应显示时间
     */
    private fun calculateAdaptiveDisplayTime(chunk: FileChunk, successRate: Double): Long {
        var time = displayTime
        
        when {
            successRate < 0.7 -> time = (time * 1.5).toLong()
            successRate > 0.95 -> time = (time * 0.8).toLong()
        }
        
        val complexity = calculateQRComplexity(chunk)
        time = (time * (1.0 + complexity * 0.2)).toLong()
        
        return time.coerceIn(MIN_DISPLAY_TIME, MAX_DISPLAY_TIME)
    }
    
    /**
     * 计算二维码复杂度
     */
    private fun calculateQRComplexity(chunk: FileChunk): Double {
        val version = chunk.qrVersion
        val dataSize = chunk.getEstimatedQRSize()
        val maxCapacity = QRVersionCalculator.getCapacity(version, chunk.errorCorrectionLevel)
        
        val utilization = dataSize.toDouble() / maxCapacity.toDouble()
        val versionFactor = version / 40.0
        
        return (utilization * 0.7 + versionFactor * 0.3)
    }
    
    /**
     * 设置回调
     */
    fun setCallbacks(
        onQRGenerated: ((Bitmap, FileChunk, Int) -> Unit)? = null,
        onProgressUpdate: ((Int, Int, Long) -> Unit)? = null,
        onCompletion: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        this.onQRGenerated = onQRGenerated
        this.onProgressUpdate = onProgressUpdate
        this.onCompletion = onCompletion
        this.onError = onError
    }
    
    /**
     * 生命周期管理
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        pauseGeneration()
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        resumeGeneration()
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        stopGeneration()
        job.cancel()
        lifecycle.removeObserver(this)
    }
    
    data class GenerationStats(
        val totalChunks: Int,
        val completedChunks: Int,
        val successRate: Double,
        val averageScanTime: Long,
        val estimatedRemainingTime: Long,
        val currentDisplayTime: Long
    )
    
    fun getStats(): GenerationStats {
        val remainingChunks = chunks.size - currentIndex
        val estimatedRemainingTime = remainingChunks * displayTime
        
        return GenerationStats(
            totalChunks = chunks.size,
            completedChunks = currentIndex,
            successRate = getSuccessRate(),
            averageScanTime = getAverageScanTime(),
            estimatedRemainingTime = estimatedRemainingTime,
            currentDisplayTime = displayTime
        )
    }
}
