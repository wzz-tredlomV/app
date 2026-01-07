package com.qrfiletransfer.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qrfiletransfer.model.FileChunk
import com.qrfiletransfer.utils.QRVersionCalculator
import kotlin.math.min

/**
 * 自适应二维码视图
 * 根据内容动态调整二维码版本和大小
 */
class AdaptiveQRView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val qrCodeWriter = QRCodeWriter()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 14f
        textAlign = Paint.Align.CENTER
    }
    
    private var qrBitmap: Bitmap? = null
    private var qrVersion: Int = 1
    private var errorCorrectionLevel = ErrorCorrectionLevel.M
    private var dataText: String? = null
    private var showInfo = true
    private var qrColor = Color.BLACK
    private var backgroundColor = Color.WHITE
    private var borderColor = Color.LTGRAY
    
    // 状态
    private var isGenerating = false
    private var generationProgress = 0f
    private var showProgress = false
    
    /**
     * 设置要显示的 QR 数据
     */
    fun setQRData(data: String, autoAdjust: Boolean = true) {
        dataText = data
        if (autoAdjust) {
            adjustQRVersion(data)
        }
        generateQRCode()
        invalidate()
    }
    
    /**
     * 设置文件块数据
     */
    fun setChunkData(chunk: FileChunk) {
        dataText = chunk.toJson()
        qrVersion = chunk.qrVersion
        errorCorrectionLevel = ErrorCorrectionLevel.valueOf(chunk.errorCorrectionLevel)
        generateQRCode()
        invalidate()
    }
    
    /**
     * 设置是否显示信息
     */
    fun setShowInfo(show: Boolean) {
        showInfo = show
        invalidate()
    }
    
    /**
     * 设置二维码颜色
     */
    fun setQRColor(color: Int) {
        qrColor = color
        qrBitmap?.let { generateQRCode() }
    }
    
    /**
     * 设置背景颜色
     */
    fun setBackgroundColor(color: Int) {
        backgroundColor = color
        invalidate()
    }
    
    /**
     * 设置边框颜色
     */
    fun setBorderColor(color: Int) {
        borderColor = color
        invalidate()
    }
    
    /**
     * 显示生成进度
     */
    fun showGenerationProgress(progress: Float) {
        showProgress = true
        generationProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }
    
    /**
     * 隐藏生成进度
     */
    fun hideGenerationProgress() {
        showProgress = false
        invalidate()
    }
    
    /**
     * 调整二维码版本以适应数据
     */
    private fun adjustQRVersion(data: String) {
        val dataSize = data.toByteArray(Charsets.UTF_8).size
        val config = QRVersionCalculator.calculateOptimalConfig(
            dataSize = dataSize,
            maxQRSize = 800,
            minVersion = 1,
            maxVersion = 40,
            preferredErrorLevel = "M"
        )
        
        qrVersion = config.version
        errorCorrectionLevel = ErrorCorrectionLevel.valueOf(config.errorCorrectionLevel)
    }
    
    /**
     * 生成二维码位图
     */
    private fun generateQRCode() {
        if (dataText.isNullOrEmpty()) return
        
        try {
            val hints = mutableMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel)
                put(EncodeHintType.QR_VERSION, qrVersion)
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }
            
            val bitMatrix: BitMatrix = qrCodeWriter.encode(
                dataText,
                BarcodeFormat.QR_CODE,
                qrVersion * 4 + 17,
                qrVersion * 4 + 17,
                hints
            )
            
            qrBitmap = createColoredBitmap(bitMatrix, qrColor, backgroundColor)
        } catch (e: Exception) {
            e.printStackTrace()
            qrBitmap = null
        }
    }
    
    /**
     * 创建彩色二维码位图
     */
    private fun createColoredBitmap(matrix: BitMatrix, color: Int, background: Int): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) color else background)
            }
        }
        
        return bitmap
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // 绘制背景
        canvas.drawColor(backgroundColor)
        
        // 绘制边框
        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(0f, 0f, width, height, paint)
        
        // 绘制二维码
        qrBitmap?.let { bitmap ->
            val qrSize = min(width, height) * 0.8f
            val left = (width - qrSize) / 2
            val top = (height - qrSize) / 2
            
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = RectF(left, top, left + qrSize, top + qrSize)
            
            paint.style = Paint.Style.FILL
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
            
            // 绘制信息文本
            if (showInfo) {
                val info = "版本: $qrVersion  纠错: ${errorCorrectionLevel}"
                canvas.drawText(info, width / 2, height - 20, textPaint)
            }
        }
        
        // 绘制生成进度
        if (showProgress) {
            drawGenerationProgress(canvas, width, height)
        }
        
        // 如果没有数据，显示提示
        if (dataText.isNullOrEmpty() && qrBitmap == null) {
            drawPlaceholder(canvas, width, height)
        }
    }
    
    /**
     * 绘制生成进度
     */
    private fun drawGenerationProgress(canvas: Canvas, width: Float, height: Float) {
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 0, 150, 255)
            style = Paint.Style.FILL
        }
        
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 200, 200, 200)
            style = Paint.Style.FILL
        }
        
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }
        
        val barWidth = width * 0.8f
        val barHeight = 20f
        val barLeft = (width - barWidth) / 2
        val barTop = height / 2 - barHeight / 2
        
        // 绘制背景条
        canvas.drawRect(barLeft, barTop, barLeft + barWidth, barTop + barHeight, backgroundPaint)
        
        // 绘制进度条
        val progressWidth = barWidth * generationProgress
        canvas.drawRect(barLeft, barTop, barLeft + progressWidth, barTop + barHeight, progressPaint)
        
        // 绘制进度文本
        val progressPercent = (generationProgress * 100).toInt()
        canvas.drawText(
            "生成中: $progressPercent%",
            width / 2,
            barTop + barHeight + 30,
            textPaint
        )
    }
    
    /**
     * 绘制占位符
     */
    private fun drawPlaceholder(canvas: Canvas, width: Float, height: Float) {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.FILL
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        
        canvas.drawText(
            "点击选择文件开始传输",
            width / 2,
            height / 2,
            placeholderPaint
        )
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 300
        
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(desiredSize, widthSize)
            else -> desiredSize
        }
        
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredSize, heightSize)
            else -> desiredSize
        }
        
        setMeasuredDimension(width, height)
    }
}
