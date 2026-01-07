package com.qrfiletransfer.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import kotlin.math.min

/**
 * 自定义二维码扫描视图
 * 包含扫描框、激光线和角标
 */
class ScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PreviewView(context, attrs, defStyleAttr) {
    
    private val scannerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f
        textAlign = Paint.Align.CENTER
    }
    
    private var frameRect: RectF? = null
    private var laserPosition = 0f
    private var laserDirection = 1
    private var isScanning = false
    private var scannerColor = Color.GREEN
    private var cornerColor = Color.RED
    private var maskColor = Color.argb(150, 0, 0, 0)
    
    // 扫描动画
    private val scannerAnimator = object : Runnable {
        override fun run() {
            if (isScanning) {
                updateLaserPosition()
                invalidate()
                postDelayed(this, 16) // 约60fps
            }
        }
    }
    
    /**
     * 设置扫描框颜色
     */
    fun setScannerColor(color: Int) {
        scannerColor = color
        invalidate()
    }
    
    /**
     * 设置角标颜色
     */
    fun setCornerColor(color: Int) {
        cornerColor = color
        invalidate()
    }
    
    /**
     * 设置遮罩颜色
     */
    fun setMaskColor(color: Int) {
        maskColor = color
        invalidate()
    }
    
    /**
     * 开始扫描动画
     */
    fun startScanning() {
        if (!isScanning) {
            isScanning = true
            laserPosition = 0f
            laserDirection = 1
            post(scannerAnimator)
        }
    }
    
    /**
     * 停止扫描动画
     */
    fun stopScanning() {
        isScanning = false
        removeCallbacks(scannerAnimator)
        invalidate()
    }
    
    /**
     * 设置扫描框大小
     */
    fun setFrameSize(width: Float, height: Float) {
        val centerX = this.width / 2f
        val centerY = this.height / 2f
        frameRect = RectF(
            centerX - width / 2,
            centerY - height / 2,
            centerX + width / 2,
            centerY + height / 2
        )
        invalidate()
    }
    
    /**
     * 获取扫描框矩形
     */
    fun getFrameRect(): RectF? = frameRect
    
    private fun updateLaserPosition() {
        frameRect?.let { rect ->
            laserPosition += 5 * laserDirection
            if (laserPosition >= rect.height() || laserPosition <= 0) {
                laserDirection *= -1
            }
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = this.width.toFloat()
        val height = this.height.toFloat()
        
        // 绘制遮罩
        drawMask(canvas, width, height)
        
        // 绘制扫描框
        drawScannerFrame(canvas)
        
        // 绘制激光线
        if (isScanning) {
            drawLaserLine(canvas)
        }
        
        // 绘制提示文本
        drawHintText(canvas, width, height)
    }
    
    /**
     * 绘制遮罩
     */
    private fun drawMask(canvas: Canvas, width: Float, height: Float) {
        frameRect?.let { rect ->
            scannerPaint.color = maskColor
            scannerPaint.style = Paint.Style.FILL
            
            // 绘制上下左右四个遮罩区域
            canvas.drawRect(0f, 0f, width, rect.top, scannerPaint) // 上
            canvas.drawRect(0f, rect.bottom, width, height, scannerPaint) // 下
            canvas.drawRect(0f, rect.top, rect.left, rect.bottom, scannerPaint) // 左
            canvas.drawRect(rect.right, rect.top, width, rect.bottom, scannerPaint) // 右
        }
    }
    
    /**
     * 绘制扫描框
     */
    private fun drawScannerFrame(canvas: Canvas) {
        frameRect?.let { rect ->
            // 绘制边框
            scannerPaint.color = scannerColor
            scannerPaint.style = Paint.Style.STROKE
            scannerPaint.strokeWidth = 2f
            canvas.drawRect(rect, scannerPaint)
            
            // 绘制角标
            drawCorners(canvas, rect)
        }
    }
    
    /**
     * 绘制角标
     */
    private fun drawCorners(canvas: Canvas, rect: RectF) {
        val cornerSize = 20f
        val cornerWidth = 5f
        
        scannerPaint.color = cornerColor
        scannerPaint.style = Paint.Style.STROKE
        scannerPaint.strokeWidth = cornerWidth
        
        // 左上角
        canvas.drawLine(rect.left, rect.top + cornerSize, rect.left, rect.top, scannerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left + cornerSize, rect.top, scannerPaint)
        
        // 右上角
        canvas.drawLine(rect.right - cornerSize, rect.top, rect.right, rect.top, scannerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerSize, scannerPaint)
        
        // 左下角
        canvas.drawLine(rect.left, rect.bottom - cornerSize, rect.left, rect.bottom, scannerPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + cornerSize, rect.bottom, scannerPaint)
        
        // 右下角
        canvas.drawLine(rect.right - cornerSize, rect.bottom, rect.right, rect.bottom, scannerPaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerSize, scannerPaint)
    }
    
    /**
     * 绘制激光线
     */
    private fun drawLaserLine(canvas: Canvas) {
        frameRect?.let { rect ->
            scannerPaint.color = scannerColor
            scannerPaint.style = Paint.Style.FILL
            
            val gradient = LinearGradient(
                rect.left, rect.top + laserPosition,
                rect.right, rect.top + laserPosition,
                Color.TRANSPARENT, scannerColor, Shader.TileMode.CLAMP
            )
            
            scannerPaint.shader = gradient
            canvas.drawRect(
                rect.left,
                rect.top + laserPosition - 1,
                rect.right,
                rect.top + laserPosition + 1,
                scannerPaint
            )
            scannerPaint.shader = null
        }
    }
    
    /**
     * 绘制提示文本
     */
    private fun drawHintText(canvas: Canvas, width: Float, height: Float) {
        frameRect?.let { rect ->
            val hint = "将二维码放入框内扫描"
            canvas.drawText(hint, width / 2, rect.bottom + 50, textPaint)
            
            val subHint = "确保二维码清晰且光线充足"
            textPaint.textSize = 12f
            canvas.drawText(subHint, width / 2, rect.bottom + 80, textPaint)
            textPaint.textSize = 16f
        } ?: run {
            val hint = "正在初始化相机..."
            canvas.drawText(hint, width / 2, height / 2, textPaint)
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopScanning()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 默认扫描框大小为屏幕宽度的70%
        val frameSize = min(w, h) * 0.7f
        setFrameSize(frameSize, frameSize)
    }
}
