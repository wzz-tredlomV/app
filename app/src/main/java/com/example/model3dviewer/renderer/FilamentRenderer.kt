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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.Buffer
import java.nio.ByteBuffer

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
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())
        
        view.camera = camera
        view.scene = scene
        
        // 1.69.2: 使用属性设置而非命名参数
        val options = View.DynamicResolutionOptions()
        options.enabled = true
        options.quality = View.QualityLevel.MEDIUM
        view.dynamicResolutionOptions = options
        
        view.isPostProcessingEnabled = true
        view.antiAliasing = View.AntiAliasing.FXAA
        view.toneMapping = View.ToneMapping.ACES
        
        // 1.69.2: UbershaderProvider只有一个参数
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
                // 1.69.2: setProjection参数顺序
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
            .color(1.0f, 0.95f, 0.85f)
            .intensity(100000.0f)
            .direction(-0.5f, -1.0f, -0.5f)
            .castShadows(true)
            .build(engine, sunlight)
        scene.addEntity(sunlight)
        
        val fillLight = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.4f, 0.5f, 0.6f)
            .intensity(30000.0f)
            .direction(0.5f, 0.3f, 0.5f)
            .build(engine, fillLight)
        scene.addEntity(fillLight)
    }
    
    suspend fun loadModel(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 清理旧模型
            filamentAsset?.let { asset ->
                scene.removeEntities(asset.entities)
                assetLoader.destroyAsset(asset)
            }
            filamentInstance = null
            
            // 读取文件
            val buffer = readFileToBuffer(filePath)
                ?: return@withContext false
            
            // 创建资源
            filamentAsset = assetLoader.createAsset(buffer)
                ?: return@withContext false
            
            // 1.69.2: getInstance()无参数，获取第一个实例
            filamentInstance = filamentAsset?.getInstance()
            
            // 1.69.2: 使用同步加载，异步API在1.69.2中有问题
            filamentAsset?.let { asset ->
                resourceLoader.loadResources(asset)
                scene.addEntities(asset.entities)
                
                // 计算相机距离
                val box = asset.boundingBox
                val halfExtent = box.halfExtent
                val size = maxOf(halfExtent[0], halfExtent[1], halfExtent[2]) * 2
                updateCamera(size * 1.5f)
            }
            
            return@withContext true
            
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    
    private fun readFileToBuffer(path: String): Buffer? {
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
    
    private fun updateCamera(distance: Float) {
        val rotX = rotationX * Math.PI / 180.0
        val rotY = rotationY * Math.PI / 180.0
        
        val x = (distance / zoom * kotlin.math.sin(rotY) * kotlin.math.cos(rotX)).toDouble()
        val y = (distance / zoom * kotlin.math.sin(rotX)).toDouble()
        val z = (distance / zoom * kotlin.math.cos(rotY) * kotlin.math.cos(rotX)).toDouble()
        
        // 1.69.2: 使用lookAt设置相机位置和朝向
        camera.lookAt(
            x, y, z,  // eye position
            0.0, 0.0, 0.0,  // center position
            0.0, 1.0, 0.0   // up vector
        )
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
    
    private fun render(frameTimeNanos: Long) {
        filamentInstance?.let { instance ->
            // 1.69.2: 通过instance获取animator
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
