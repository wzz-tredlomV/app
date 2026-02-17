package com.example.model3dviewer.renderer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
        
        return try {
            withContext(Dispatchers.IO) {
                val fileSize = getFileSize(filePath)
                if (fileSize == 0L) {
                    throw IllegalArgumentException("无法读取文件或文件为空")
                }
                if (fileSize > MAX_FILE_SIZE) {
                    throw IllegalArgumentException("文件过大: ${fileSize / 1024 / 1024}MB, 最大支持${MAX_FILE_SIZE / 1024 / 1024}MB")
                }
                
                // 验证文件格式
                if (!isValidModelFile(filePath)) {
                    throw IllegalArgumentException("不支持的文件格式，仅支持 .glb 或 .gltf")
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
                } ?: throw IllegalArgumentException("无法读取文件内容")
                
                onProgress(60)
                
                // 验证 GLB 文件头（如果是 GLB 格式）
                if (isProbablyGlbFile(filePath) && !isValidGlbHeader(buffer)) {
                    throw IllegalArgumentException("无效的 GLB 文件格式")
                }
                
                val asset = withTimeoutOrNull(LOAD_TIMEOUT) {
                    assetLoader.createAsset(buffer)
                } ?: throw IllegalStateException("创建资源超时")
                
                if (asset == null) {
                    throw IllegalStateException("无法解析模型文件，请确保是有效的 GLB/GLTF 格式")
                }
                
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
    
    /**
     * 验证文件是否为支持的 3D 模型格式
     */
    private fun isValidModelFile(path: String): Boolean {
        return try {
            val uri = Uri.parse(path)
            
            // 1. 尝试从 ContentResolver 获取 MIME 类型
            val mimeType = context.contentResolver.getType(uri)
            android.util.Log.d("FilamentRenderer", "File MIME type: $mimeType")
            
            if (mimeType != null) {
                when {
                    mimeType.contains("gltf") || 
                    mimeType.contains("glb") ||
                    mimeType == "application/octet-stream" ||
                    mimeType == "model/gltf-binary" ||
                    mimeType == "model/gltf+json" -> return true
                }
            }
            
            // 2. 从文件名检查扩展名
            val fileName = getFileNameFromUri(uri)?.lowercase() ?: ""
            android.util.Log.d("FilamentRenderer", "File name: $fileName")
            
            if (fileName.endsWith(".glb") || fileName.endsWith(".gltf")) {
                return true
            }
            
            // 3. 如果无法确定，放行让后续验证处理（可能是从流中获取的文件）
            true
        } catch (e: Exception) {
            android.util.Log.w("FilamentRenderer", "Cannot validate file format", e)
            true // 出错时放行
        }
    }
    
    /**
     * 判断文件可能是 GLB 格式（用于决定是否需要验证 GLB 头）
     */
    private fun isProbablyGlbFile(path: String): Boolean {
        val fileName = getFileNameFromUri(Uri.parse(path))?.lowercase() ?: ""
        return fileName.endsWith(".glb")
    }
    
    /**
     * 从 URI 获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            // 尝试从 URI 路径获取
            uri.path?.let { path ->
                val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
                val lastSegment = decodedPath.substringAfterLast('/')
                if (lastSegment.isNotEmpty() && !lastSegment.contains(":")) {
                    return lastSegment
                }
            }
            
            // 尝试通过 ContentResolver 查询 DISPLAY_NAME
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        return cursor.getString(displayNameIndex)
                    }
                }
            }
            
            // 备用：使用 lastPathSegment
            uri.lastPathSegment?.let { 
                java.net.URLDecoder.decode(it, "UTF-8")
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }
    
    /**
     * 验证 GLB 文件头魔数
     * GLB 文件以 0x46546C67 ("glTF") 开头
     */
    private fun isValidGlbHeader(buffer: Buffer): Boolean {
        return try {
            if (buffer is ByteBuffer && buffer.remaining() >= 4) {
                // 保存当前位置
                val originalPosition = buffer.position()
                
                buffer.position(0)
                val magic = buffer.getInt()
                
                // 恢复位置
                buffer.position(originalPosition)
                
                magic == 0x46546C67 // "glTF" in little-endian
            } else {
                false
            }
        } catch (e: Exception) {
            false
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
            val uri = Uri.parse(path)
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun readSmallFile(path: String): Buffer? {
        return try {
            val uri = Uri.parse(path)
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
            val uri = Uri.parse(path)
            
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
            withContext(Dispatchers.IO) {
                resourceLoader.loadResources(asset)
            }
            
            withContext(Dispatchers.Main) {
                onProgress(90)
                scene.addEntities(asset.entities)
                
                val box = asset.boundingBox
                val halfExtent = box.halfExtent
                val size = maxOf(halfExtent[0], halfExtent[1], halfExtent[2]) * 2.0f
                
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
