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
    private var animator: Animator? = null
    
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
        
        view.dynamicResolutionOptions = View.DynamicResolutionOptions().apply {
            enabled = true
            quality = View.QualityLevel.MEDIUM
        }
        
        view.isPostProcessingEnabled = true
        view.antiAliasing = View.AntiAliasing.FXAA
        view.toneMapping = View.ToneMapping.ACES_LEGACY
        
        materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine)
        
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                renderer.setDisplaySurface(displayHelper.display, surface)
            }
            override fun onDetachedFromSurface() {
                renderer.flushAndWait()
            }
            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                camera.setProjection(45.0, width.toDouble() / height, 0.1, 100.0)
            }
        }
        uiHelper.attachTo(surfaceView)
        displayHelper = DisplayHelper(context)
        
        setupLighting()
        choreographer.postFrameCallback(frameCallback)
    }
    
    private fun setupLighting() {
        val engine = this.engine
        
        // 创建默认光源
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
        
        // 环境光（简化版，实际应加载KTX文件）
        val ibl = LightManager.Builder(LightManager.Type.AREA)
            .intensity(50000f)
            .build(engine, EntityManager.get().create())
    }
    
    suspend fun loadModel(filePath: String) = withContext(Dispatchers.IO) {
        filamentAsset?.let { asset ->
            scene.removeEntities(asset.entities)
            assetLoader.destroyAsset(asset)
        }
        
        val buffer = readFileToBuffer(filePath)
        filamentAsset = assetLoader.createAsset(buffer)
        
        filamentAsset?.let { asset ->
            resourceLoader.loadResources(asset)
            scene.addEntities(asset.entities)
            animator = asset.animator
            
            val box = asset.boundingBox
            val size = length(box.max - box.min)
            
            updateCamera(size * 1.5f)
        }
    }
    
    private fun readFileToBuffer(path: String): Buffer {
        val uri = android.net.Uri.parse(path)
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(java.nio.ByteOrder.nativeOrder())
                put(bytes)
                flip()
            }
        } ?: throw IllegalArgumentException("无法读取文件: $path")
    }
    
    private fun updateCamera(distance: Float) {
        val rotX = rotationX * Math.PI / 180.0
        val rotY = rotationY * Math.PI / 180.0
        
        val x = (distance / zoom * kotlin.math.sin(rotY) * kotlin.math.cos(rotX)).toFloat()
        val y = (distance / zoom * kotlin.math.sin(rotX)).toFloat()
        val z = (distance / zoom * kotlin.math.cos(rotY) * kotlin.math.cos(rotX)).toFloat()
        
        camera.position = Float3(x, y, z)
        camera.lookAt(Float3(0f, 0f, 0f), Float3(0f, 1f, 0f))
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
        animator?.apply {
            if (animationCount > 0) {
                val time = (frameTimeNanos / 1_000_000_000.0).toFloat()
                applyAnimation(0, time % getAnimationDuration(0))
                updateBoneMatrices()
            }
        }
        
        filamentAsset?.let { asset ->
            val box = asset.boundingBox
            val size = length(box.max - box.min)
            updateCamera(size * 1.5f)
        }
        
        if (uiHelper.isReadyToRender) {
            renderer.render(view)
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
        filamentAsset?.let { assetLoader.destroyAsset(it) }
        resourceLoader.destroy()
        assetLoader.destroy()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        engine.destroy()
    }
}
