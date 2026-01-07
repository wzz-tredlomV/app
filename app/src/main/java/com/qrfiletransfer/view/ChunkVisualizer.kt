package com.qrfiletransfer.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil
import kotlin.math.min

/**
 * 块可视化视图
 * 显示文件分块的传输状态
 */
class ChunkVisualizer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val MAX_CHUNKS_PER_ROW = 20
        private const val CHUNK_SIZE = 30f
        private const val CHUNK_SPACING = 4f
    }
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 12f
        textAlign = Paint.Align.CENTER
    }
    
    // 块状态数据
    private var totalChunks = 0
    private var transmittedChunks = 0
    private var receivedChunks = 0
    private var verifiedChunks = 0
    private var failedChunks = 0
    
    // 颜色定义
    private val colorPending = Color.LTGRAY
    private val colorTransmitted = Color.BLUE
    private val colorReceived = Color.GREEN
    private val colorVerified = Color.parseColor("#4CAF50")
    private val colorFailed = Color.RED
    
    /**
     * 设置块总数
     */
    fun setTotalChunks(count: Int) {
        totalChunks = count
        invalidate()
    }
    
    /**
     * 设置传输块数
     */
    fun setTransmittedChunks(count: Int) {
        transmittedChunks = count
        invalidate()
    }
    
    /**
     * 设置接收块数
     */
    fun setReceivedChunks(count: Int) {
        receivedChunks = count
        invalidate()
    }
    
    /**
     * 设置验证块数
     */
    fun setVerifiedChunks(count: Int) {
        verifiedChunks = count
        invalidate()
    }
    
    /**
     * 设置失败块数
     */
    fun setFailedChunks(count: Int) {
        failedChunks = count
        invalidate()
    }
    
    /**
     * 更新所有状态
     */
    fun updateChunkStatus(
        total: Int,
        transmitted: Int,
        received: Int,
        verified: Int,
        failed: Int
    ) {
        totalChunks = total
        transmittedChunks = transmitted
        receivedChunks = received
        verifiedChunks = verified
        failedChunks = failed
        invalidate()
    }
    
    /**
     * 获取传输进度
     */
    fun getTransmissionProgress(): Float {
        return if (totalChunks > 0) {
            transmittedChunks.toFloat() / totalChunks.toFloat()
        } else {
            0f
        }
    }
    
    /**
     * 获取接收进度
     */
    fun getReceiveProgress(): Float {
        return if (totalChunks > 0) {
            receivedChunks.toFloat() / totalChunks.toFloat()
        } else {
            0f
        }
    }
    
    /**
     * 获取验证进度
     */
    fun getVerificationProgress(): Float {
        return if (totalChunks > 0) {
            verifiedChunks.toFloat() / totalChunks.toFloat()
        } else {
            0f
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (totalChunks <= 0) {
            drawEmptyState(canvas)
            return
        }
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // 计算布局
        val rows = ceil(totalChunks.toFloat() / MAX_CHUNKS_PER_ROW).toInt()
        val actualChunksPerRow = ceil(totalChunks.toFloat() / rows).toInt()
        
        val totalWidthNeeded = actualChunksPerRow * (CHUNK_SIZE + CHUNK_SPACING) - CHUNK_SPACING
        val totalHeightNeeded = rows * (CHUNK_SIZE + CHUNK_SPACING) - CHUNK_SPACING
        
        val startX = (width - totalWidthNeeded) / 2
        val startY = (height - totalHeightNeeded) / 2
        
        // 绘制每个块
        for (i in 0 until totalChunks) {
            val row = i / actualChunksPerRow
            val col = i % actualChunksPerRow
            
            val x = startX + col * (CHUNK_SIZE + CHUNK_SPACING)
            val y = startY + row * (CHUNK_SIZE + CHUNK_SPACING)
            
            drawChunk(canvas, i, x, y)
        }
        
        // 绘制统计信息
        drawStatistics(canvas, width, height)
    }
    
    /**
     * 绘制单个块
     */
    private fun drawChunk(canvas: Canvas, index: Int, x: Float, y: Float) {
        // 确定块颜色
        val color = when {
            index < verifiedChunks -> colorVerified
            index < receivedChunks -> colorReceived
            index < transmittedChunks -> colorTransmitted
            index < failedChunks -> colorFailed
            else -> colorPending
        }
        
        // 绘制块背景
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRect(x, y, x + CHUNK_SIZE, y + CHUNK_SIZE, paint)
        
        // 绘制块边框
        paint.color = Color.DKGRAY
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(x, y, x + CHUNK_SIZE, y + CHUNK_SIZE, paint)
        
        // 绘制块编号（仅在块较大时显示）
        if (CHUNK_SIZE > 20) {
            textPaint.color = Color.BLACK
            canvas.drawText(
                (index + 1).toString(),
                x + CHUNK_SIZE / 2,
                y + CHUNK_SIZE / 2 + textPaint.textSize / 3,
                textPaint
            )
        }
        
        // 绘制状态指示器
        drawStatusIndicator(canvas, index, x, y)
    }
    
    /**
     * 绘制状态指示器
     */
    private fun drawStatusIndicator(canvas: Canvas, index: Int, x: Float, y: Float) {
        if (index < failedChunks) {
            // 绘制失败标记（X）
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            
            val margin = CHUNK_SIZE / 4
            canvas.drawLine(
                x + margin, y + margin,
                x + CHUNK_SIZE - margin, y + CHUNK_SIZE - margin,
                paint
            )
            canvas.drawLine(
                x + CHUNK_SIZE - margin, y + margin,
                x + margin, y + CHUNK_SIZE - margin,
                paint
            )
        } else if (index < verifiedChunks) {
            // 绘制验证标记（✓）
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            
            val margin = CHUNK_SIZE / 4
            val checkSize = CHUNK_SIZE - 2 * margin
            
            canvas.drawLine(
                x + margin, y + CHUNK_SIZE / 2,
                x + CHUNK_SIZE / 2, y + CHUNK_SIZE - margin,
                paint
            )
            canvas.drawLine(
                x + CHUNK_SIZE / 2, y + CHUNK_SIZE - margin,
                x + CHUNK_SIZE - margin, y + margin,
                paint
            )
        }
    }
    
    /**
     * 绘制统计信息
     */
    private fun drawStatistics(canvas: Canvas, width: Float, height: Float) {
        val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 14f
        }
        
        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
        }
        
        val stats = """
            总计: $totalChunks
            传输: $transmittedChunks
            接收: $receivedChunks
            验证: $verifiedChunks
            失败: $failedChunks
        """.trimIndent()
        
        val lines = stats.split("\n")
        var y = height - lines.size * (statsPaint.textSize + 5)
        
        for (line in lines) {
            canvas.drawText(line, 10f, y, statsPaint)
            y += statsPaint.textSize + 5
        }
        
        // 绘制图例
        drawLegend(canvas, width, height)
    }
    
    /**
     * 绘制图例
     */
    private fun drawLegend(canvas: Canvas, width: Float, height: Float) {
        val legendItems = listOf(
            "待处理" to colorPending,
            "传输中" to colorTransmitted,
            "已接收" to colorReceived,
            "已验证" to colorVerified,
            "失败" to colorFailed
        )
        
        val boxSize = 10f
        val spacing = 80f
        var x = width - spacing * legendItems.size
        
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10f
        }
        
        for ((label, color) in legendItems) {
            // 绘制颜色框
            paint.color = color
            paint.style = Paint.Style.FILL
            canvas.drawRect(x, 10f, x + boxSize, 10f + boxSize, paint)
            
            // 绘制标签
            canvas.drawText(label, x + boxSize + 5, 10f + boxSize, textPaint)
            
            x += spacing
        }
    }
    
    /**
     * 绘制空状态
     */
    private fun drawEmptyState(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }
        
        canvas.drawText(
            "暂无数据",
            width / 2,
            height / 2,
            textPaint
        )
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 400
        val desiredHeight = 300
        
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
