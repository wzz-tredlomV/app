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
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class FilamentRenderer(private val context: Context, private val surfaceView: SurfaceView) {
    
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader
    private lateinit var materialProvider: MaterialProvider
    
    private val filamentAsset = AtomicReference<FilamentAsset?>(null)
    private val filamentInstance = AtomicReference<FilamentInstance?>(null)
    private var swapChain: SwapChain? = null
    
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private lateinit var displayHelper: DisplayHelper
    private val choreographer = Choreographer.getInstance()
    
    @Volatile
    private var rotationX = 0f
    
    @Volatile
    private var rotationY = 0f
    
    @Volatile
    private var zoom = 1f
    
    private val isDestroyed = AtomicBoolean(false)
    private val isLoading = AtomicBoolean(false)
    
    companion object {
        const val MAX_FILE_SIZE = 100 * 1024 * 1024L
        const val BUFFER_SIZE = 8 * 1024 * 1024
        const val LOAD_TIMEOUT = 120000L
    }
    
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isDestroyed.get()) {
                render(frameTimeNanos)
                choreographer.postFrameCallback(this)
            }
        }
    }
    
    init {
        setupFilament()
    }
    
    private fun setupFilament() {
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
        options.quality = View.QualityLevel.LOW
        view.dynamicResolutionOptions = options
        
        view.isPostProcessingEnabled = false
        view.antiAliasing = View.AntiAliasing.NONE
        view.toneMapping = View.ToneMapping.LINEAR
        
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
        val sunlight = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(50000.0f)
            .direction(0.0f, -1.0f, 0.0f)
            .castShadows(false)
            .build(engine, sunlight)
        scene.addEntity(sunlight)
    }
    
    suspend fun loadModel(filePath: String, onProgress: (Int) -> Unit): Boolean {
        if (!isLoading.compareAndSet(false, true)) {
            return false
        }
        
        // 检查文件格式
        val lowerPath = filePath.lowercase()
        if (!lowerPath.endsWith(".glb") && !lowerPath.endsWith(".gltf")) {
            isLoading.set(false)
            throw IllegalArgumentException("不支持的文件格式，仅支持 .glb 或 .gltf")
        }
        
        return try {
            withContext(Dispatchers.IO) {
                val fileSize = getFileSize(filePath)
                if (fileSize > MAX_FILE_SIZE) {
                    throw IllegalArgumentException("文件过大: ${fileSize / 1024 / 1024}MB, 最大支持${MAX_FILE_SIZE / 1024 / 1024}MB")
                }
                
                // 在主线程清理当前资源
                withContext(Dispatchers.Main) {
                    clearCurrentAsset()
                }
                
                onProgress(10)
                
                val buffer = if (fileSize > BUFFER_SIZE) {
                    readLargeFile(filePath, onProgress)
                } else {
                    readSmallFile(filePath)?.also { onProgress(30) }
                }
                
                onProgress(60)
                
                buffer ?: return@withContext false
                
                val asset = withTimeoutOrNull(LOAD_TIMEOUT) {
                    assetLoader.createAsset(buffer)
                } ?: throw IllegalStateException("创建资源超时")
                
                onProgress(80)
                
                filamentAsset.set(asset)
                filamentInstance.set(asset.getInstance())
                
                loadResourcesWithTimeout(asset, onProgress)
                
                onProgress(100)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            isLoading.set(false)
        }
    }
    
    private suspend fun clearCurrentAsset() {
        filamentAsset.getAndSet(null)?.let { asset ->
            scene.removeEntities(asset.entities)
            assetLoader.destroyAsset(asset)
        }
        filamentInstance.set(null)
        System.gc()
    }
    
    private fun getFileSize(path: String): Long {
        return try {
            val uri = android.net.Uri.parse(path)
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
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
    
    private fun readLargeFile(path: String, onProgress: (Int) -> Unit): Buffer? {
        return try {
            val uri = android.net.Uri.parse(path)
            
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val channel = fis.channel
                    val fileSize = channel.size()
                    
                    if (fileSize > Int.MAX_VALUE) {
                        return null
                    }
                    
                    val buffer = ByteBuffer.allocateDirect(fileSize.toInt())
                    buffer.order(java.nio.ByteOrder.nativeOrder())
                    
                    var position = 0L
                    val chunkSize = BUFFER_SIZE.toLong()
                    
                    while (position < fileSize) {
                        val remaining = fileSize - position
                        val size = minOf(chunkSize, remaining).toInt()
                        
                        // 使用普通读取而非内存映射，避免内存泄漏
                        val bytes = ByteArray(size)
                        val tempBuffer = ByteBuffer.wrap(bytes)
                        channel.read(tempBuffer)
                        buffer.put(bytes)
                        
                        position += size
                        
                        val progress = 10 + (position * 50 / fileSize).toInt()
                        onProgress(progress.coerceIn(10, 60))
                        
                        if (position % (chunkSize * 2) == 0L) {
                            Thread.yield()
                        }
                    }
                    
                    buffer.flip()
                    buffer
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private suspend fun loadResourcesWithTimeout(asset: FilamentAsset, onProgress: (Int) -> Unit) {
        withTimeoutOrNull(LOAD_TIMEOUT) {
            // 在 IO 线程加载资源
            withContext(Dispatchers.IO) {
                resourceLoader.loadResources(asset)
            }
            
            // 在主线程操作场景和相机
            withContext(Dispatchers.Main) {
                onProgress(90)
                scene.addEntities(asset.entities)
                
                val box = asset.boundingBox
                val halfExtent = box.halfExtent
                val size = maxOf(halfExtent[0], halfExtent[1], halfExtent[2]) * 2.0f
                
                // 直接计算相机位置，避免调用 updateCamera 导致的线程问题
                val distance = size * 1.5f
                val rotX = rotationX * Math.PI / 180.0
                val rotY = rotationY * Math.PI / 180.0
                
                val x = (distance / zoom * kotlin.math.sin(rotY) * kotlin.math.cos(rotX))
                val y = (distance / zoom * kotlin.math.sin(rotX))
                val z = (distance / zoom * kotlin.math.cos(rotY) * kotlin.math.cos(rotX))
                
                camera.lookAt(x, y, z, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
            }
        } ?: throw IllegalStateException("加载资源超时")
    }
    
    fun addRotation(deltaX: Float, deltaY: Float) {
        rotationX += deltaX
        rotationY += deltaY
        rotationX = rotationX.coerceIn(-90f, 90f)
        rotationY = rotationY % 360f
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
    
    private fun render(frameTimeNanos: Long) {
        // 安全获取实例
        val instance = filamentInstance.get() ?: return
        
        try {
            val animator = instance.animator
            if (animator.animationCount > 0) {
                val duration = animator.getAnimationDuration(0)
                if (duration > 0) {
                    val time = (frameTimeNanos / 1_000_000_000.0).toFloat()
                    animator.applyAnimation(0, time % duration)
                    animator.updateBoneMatrices()
                }
            }
        } catch (e: Exception) {
            // 动画错误不应导致崩溃
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
        if (isDestroyed.compareAndSet(false, true)) {
            choreographer.removeFrameCallback(frameCallback)
            uiHelper.detach()
            swapChain?.let { engine.destroySwapChain(it) }
            filamentAsset.get()?.let { assetLoader.destroyAsset(it) }
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
}

