package com.example.model3dviewer.renderer

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.*
import kotlinx.coroutines.*
import java.nio.Buffer
import java.nio.ByteBuffer
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

class FilamentRenderer(private val context: Context, private val surfaceView: SurfaceView) {
    
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader
    private lateinit var materialProvider: MaterialProvider
    
    private var filamentAsset: FilamentAsset? = null
    private var filamentInstance: FilamentInstance? = null
    private var swapChain: SwapChain? = null
    
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private lateinit var displayHelper: DisplayHelper
    private val choreographer = Choreographer.getInstance()
    
    private var rotationX = 0f
    private var rotationY = 0f
    private var zoom = 1f
    
    // 大文件分块读取配置
    companion object {
        const val MAX_FILE_SIZE = 100 * 1024 * 1024 // 100MB限制
        const val BUFFER_SIZE = 8 * 1024 * 1024 // 8MB缓冲区
        const val LOAD_TIMEOUT = 120000L // 120秒超时
    }
    
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            render(frameTimeNanos)
            choreographer.postFrameCallback(this)
        }
    }
    
    init {
        setupFilament()
    }
    
    private fun setupFilament() {
        // 大内存配置
        System.setProperty("filament.memory.pool-size", "64")
        System.setProperty("filament.memory.warn-threshold", "0.8")
        
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())
        
        view.camera = camera
        view.scene = scene
        
        val options = View.DynamicResolutionOptions()
        options.enabled = true
        options.quality = View.QualityLevel.LOW // 大文件使用低质量动态分辨率
        options.minScale = 0.5f
        options.maxScale = 1.0f
        view.dynamicResolutionOptions = options
        
        // 性能优化设置
        view.isPostProcessingEnabled = false // 禁用后处理提升性能
        view.antiAliasing = View.AntiAliasing.NONE // 禁用抗锯齿
        view.toneMapping = View.ToneMapping.LINEAR // 简单色调映射
        
        // 层级剔除
        view.setScreenSpaceRefractionEnabled(false)
        
        materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine)
        
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
            }
            override fun onDetachedFromSurface() {
                swapChain?.let { 
                    engine.destroySwapChain(it)
                    swapChain = null
                }
                engine.flushAndWait()
            }
            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                val aspect = width.toDouble() / height.toDouble()
                camera.setProjection(45.0, aspect, 0.1, 1000.0, Camera.Fov.VERTICAL)
            }
        }
        uiHelper.attachTo(surfaceView)
        displayHelper = DisplayHelper(context)
        
        setupLighting()
        choreographer.postFrameCallback(frameCallback)
    }
    
    private fun setupLighting() {
        // 简化光照，减少计算
        val sunlight = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(50000.0f)
            .direction(0.0f, -1.0f, 0.0f)
            .castShadows(false) // 禁用阴影提升性能
            .build(engine, sunlight)
        scene.addEntity(sunlight)
    }
    
    // 大文件加载入口
    suspend fun loadModel(filePath: String, onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            // 检查文件大小
            val fileSize = getFileSize(filePath)
            if (fileSize > MAX_FILE_SIZE) {
                throw IllegalArgumentException("文件过大: ${fileSize / 1024 / 1024}MB, 最大支持${MAX_FILE_SIZE / 1024 / 1024}MB")
            }
            
            // 清理旧模型
            withContext(Dispatchers.Main) {
                filamentAsset?.let { asset ->
                    scene.removeEntities(asset.entities)
                    assetLoader.destroyAsset(asset)
                }
                filamentInstance = null
                // 强制垃圾回收
                System.gc()
                Runtime.getRuntime().gc()
            }
            
            onProgress(10)
            
            // 分块读取大文件
            val buffer = if (fileSize > BUFFER_SIZE) {
                readLargeFile(filePath, onProgress)
            } else {
                readSmallFile(filePath)
            }
            
            onProgress(60)
            
            buffer ?: return@withContext false
            
            // 超时保护创建资源
            val asset = withTimeoutOrNull(LOAD_TIMEOUT) {
                assetLoader.createAsset(buffer)
            } ?: throw IllegalStateException("创建资源超时")
            
            onProgress(80)
            
            filamentAsset = asset
            filamentInstance = asset.getInstance()
            
            // 分步加载资源，避免阻塞
            loadResourcesWithTimeout(asset, onProgress)
            
            onProgress(100)
            return@withContext true
            
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    
    private fun getFileSize(path: String): Long {
        return try {
            val uri = android.net.Uri.parse(path)
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    // 小文件快速读取
    private fun readSmallFile(path: String): Buffer? {
        return try {
            val uri = android.net.Uri.parse(path)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isEmpty()) return null
                
                ByteBuffer.allocateDirect(bytes.size).apply {
                    order(java.nio.ByteOrder.nativeOrder())
                    put(bytes)
                    flip()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // 大文件分块内存映射读取
    private fun readLargeFile(path: String, onProgress: (Int) -> Unit): Buffer? {
        return try {
            val uri = android.net.Uri.parse(path)
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            
            FileInputStream(parcelFileDescriptor.fileDescriptor).use { fis ->
                val channel = fis.channel
                val fileSize = channel.size()
                
                // 使用内存映射，避免一次性加载
                val buffer = ByteBuffer.allocateDirect(fileSize.toInt())
                buffer.order(java.nio.ByteOrder.nativeOrder())
                
                var position = 0L
                val chunkSize = BUFFER_SIZE
                
                while (position < fileSize) {
                    val remaining = fileSize - position
                    val size = minOf(chunkSize, remaining).toInt()
                    
                    val chunk = channel.map(FileChannel.MapMode.READ_ONLY, position, size.toLong())
                    val bytes = ByteArray(size)
                    chunk.get(bytes)
                    buffer.put(bytes)
                    
                    position += size
                    
                    // 更新进度
                    val progress = 10 + (position * 50 / fileSize).toInt()
                    onProgress(progress.coerceIn(10, 60))
                    
                    // 每读取一块让出时间片，避免阻塞
                    if (position % (chunkSize * 2) == 0L) {
                        Thread.yield()
                    }
                }
                
                buffer.flip()
                parcelFileDescriptor.close()
                buffer
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // 带超时的资源加载
    private suspend fun loadResourcesWithTimeout(asset: FilamentAsset, onProgress: (Int) -> Unit) {
        withTimeoutOrNull(LOAD_TIMEOUT) {
            withContext(Dispatchers.IO) {
                // 分步加载，每步检查取消
                resourceLoader.loadResources(asset)
                
                // 切换到主线程添加实体
                withContext(Dispatchers.Main) {
                    onProgress(90)
                    scene.addEntities(asset.entities)
                    
                    // 计算相机距离
                    val box = asset.boundingBox
                    val halfExtent = box.halfExtent
                    val size = maxOf(halfExtent[0], halfExtent[1], halfExtent[2]) * 2
                    updateCamera(size * 1.5f)
                }
            }
        } ?: throw IllegalStateException("加载资源超时")
    }
    
    // 相机控制
    private fun updateCamera(distance: Float) {
        val rotX = rotationX * Math.PI / 180.0
        val rotY = rotationY * Math.PI / 180.0
        
        val x = (distance / zoom * kotlin.math.sin(rotY) * kotlin.math.cos(rotX)).toDouble()
        val y = (distance / zoom * kotlin.math.sin(rotX)).toDouble()
        val z = (distance / zoom * kotlin.math.cos(rotY) * kotlin.math.cos(rotX)).toDouble()
        
        camera.lookAt(x, y, z, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    }
    
    fun addRotation(deltaX: Float, deltaY: Float) {
        rotationX += deltaX
        rotationY += deltaY
        rotationX = rotationX.coerceIn(-90f, 90f)
    }
    
    fun applyZoom(factor: Float) {
        zoom *= factor
        zoom = zoom.coerceIn(0.1f, 5.0f)
    }
    
    fun resetCamera() {
        rotationX = 0f
        rotationY = 0f
        zoom = 1f
    }
    
    // LOD控制
    fun setLODLevel(level: Int) {
        filamentAsset?.let { asset ->
            // 根据距离设置LOD
            for (entity in asset.entities) {
                val renderable = engine.renderableManager.getInstance(entity)
                if (renderable != 0) {
                    engine.renderableManager.setLODRange(renderable, level, level)
                }
            }
        }
    }
    
    private fun render(frameTimeNanos: Long) {
        filamentInstance?.let { instance ->
            val animator = instance.animator
            if (animator.animationCount > 0) {
                val time = (frameTimeNanos / 1_000_000_000.0).toFloat()
                animator.applyAnimation(0, time % animator.getAnimationDuration(0))
                animator.updateBoneMatrices()
            }
        }
        
        swapChain?.let { sc ->
            if (renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }
    }
    
    fun onResume() {
        choreographer.postFrameCallback(frameCallback)
    }
    
    fun onPause() {
        choreographer.removeFrameCallback(frameCallback)
    }
    
    fun destroy() {
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()
        swapChain?.let { engine.destroySwapChain(it) }
        filamentAsset?.let { assetLoader.destroyAsset(it) }
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroy()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        engine.destroy()
    }
}

