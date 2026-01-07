package com.qrfiletransfer.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * 自定义进度指示器
 * 显示传输进度、速度和估计时间
 */
class ProgressIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // 进度属性
    private var progress = 0f
    private var secondaryProgress = 0f
    private var speed = 0f
    private var estimatedTime = 0L
    private var statusText = "准备中..."
    
    // 绘制工具
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4CAF50")
    }
    
    private val secondaryProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2196F3")
    }
    
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E0E0E0")
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }
    
    private val infoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#666666")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#BDBDBD")
        strokeWidth = 2f
    }
    
    private val path = Path()
    private val rect = RectF()
    
    /**
     * 设置进度
     */
    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }
    
    /**
     * 设置次要进度（用于显示缓冲）
     */
    fun setSecondaryProgress(value: Float) {
        secondaryProgress = value.coerceIn(0f, 1f)
        invalidate()
    }
    
    /**
     * 设置传输速度
     */
    fun setSpeed(bytesPerSecond: Float) {
        speed = bytesPerSecond
        invalidate()
    }
    
    /**
     * 设置估计时间
     */
    fun setEstimatedTime(milliseconds: Long) {
        estimatedTime = milliseconds
        invalidate()
    }
    
    /**
     * 设置状态文本
     */
    fun setStatusText(text: String) {
        statusText = text
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 20f
        val cornerRadius = 15f
        
        // 绘制背景
        rect.set(padding, padding, width - padding, height - padding)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint)
        
        // 绘制次要进度（缓冲）
        if (secondaryProgress > 0) {
            val secondaryWidth = rect.width() * secondaryProgress
            rect.right = rect.left + secondaryWidth
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, secondaryProgressPaint)
        }
        
        // 绘制主进度
        if (progress > 0) {
            val progressWidth = rect.width() * progress
            rect.set(padding, padding, padding + progressWidth, height - padding)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, progressPaint)
        }
        
        // 绘制边框
        rect.set(padding, padding, width - padding, height - padding)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        
        // 绘制进度文本
        val progressPercent = (progress * 100).toInt()
        val centerX = width / 2
        val centerY = height / 2
        
        canvas.drawText(
            "$progressPercent%",
            centerX,
            centerY - 20,
            textPaint
        )
        
        // 绘制状态文本
        canvas.drawText(
            statusText,
            centerX,
            centerY + 40,
            infoTextPaint
        )
        
        // 绘制速度信息
        if (speed > 0) {
            val speedText = formatSpeed(speed)
            val timeText = formatTime(estimatedTime)
            
            canvas.drawText(
                "速度: $speedText",
                centerX,
                centerY + 80,
                infoTextPaint
            )
            
            canvas.drawText(
                "剩余: $timeText",
                centerX,
                centerY + 120,
                infoTextPaint
            )
        }
    }
    
    /**
     * 格式化速度
     */
    private fun formatSpeed(bytesPerSecond: Float): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSecond / (1024 * 1024))
            bytesPerSecond >= 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024)
            else -> String.format("%.0f B/s", bytesPerSecond)
        }
    }
    
    /**
     * 格式化时间
     */
    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        
        return when {
            seconds >= 3600 -> {
                val hours = seconds / 3600
                val mins = (seconds % 3600) / 60
                val secs = seconds % 60
                "${hours}h ${mins}m ${secs}s"
            }
            seconds >= 60 -> {
                val mins = seconds / 60
                val secs = seconds % 60
                "${mins}m ${secs}s"
            }
            else -> "${seconds}s"
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 400
        val desiredHeight = 200
        
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(desiredWidth, widthSize)
            else -> desiredWidth
        }
        
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredHeight, heightSize)
            else -> desiredHeight
        }
        
        setMeasuredDimension(width, height)
    }
}
