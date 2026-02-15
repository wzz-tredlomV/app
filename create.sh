#!/bin/bash
# create_complete_project.sh - 创建完整的优化版Android 3D查看器项目

set -e  # 遇到错误立即退出

PROJECT_NAME="Model3DViewer"
PACKAGE_NAME="com.example.model3dviewer"
BASE_DIR="$PWD/$PROJECT_NAME"
APP_DIR="$BASE_DIR/app"

echo "🚀 开始创建 Android 3D Viewer 项目..."

# 创建完整目录结构
echo "📁 创建目录结构..."
mkdir -p "$APP_DIR"/src/main/{java/com/example/model3dviewer/{renderer,model,ui,adapter,utils},res/{layout,values,raw,drawable,mipmap-hdpi,mipmap-mdpi,mipmap-xhdpi,mipmap-xxhdpi,mipmap-xxxhdpi,menu},assets/environments}
mkdir -p "$APP_DIR"/src/test/java/com/example/model3dviewer
mkdir -p "$BASE_DIR"/gradle/wrapper
mkdir -p "$BASE_DIR"/.idea

# ============ 根目录文件 ============

cat > "$BASE_DIR/settings.gradle.kts" << 'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://storage.googleapis.com/download.tensorflow.org/maven") }
    }
}
rootProject.name = "Model3DViewer"
include(":app")
EOF

cat > "$BASE_DIR/build.gradle.kts" << 'EOF'
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
}
EOF

cat > "$BASE_DIR/gradle.properties" << 'EOF'
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
android.enableJetifier=true
EOF

cat > "$BASE_DIR/local.properties" << EOF
sdk.dir=$ANDROID_SDK_ROOT
ndk.dir=$ANDROID_NDK_ROOT
EOF

# Gradle Wrapper
cat > "$BASE_DIR/gradle/wrapper/gradle-wrapper.properties" << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

# ============ App Module Build Files ============

cat > "$APP_DIR/build.gradle.kts" << 'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.model3dviewer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.model3dviewer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    
    val filamentVersion = "1.50.0"
    implementation("com.google.android.filament:filament-android:$filamentVersion")
    implementation("com.google.android.filament:filament-utils-android:$filamentVersion")
    implementation("com.google.android.filament:gltfio-android:$filamentVersion")
    
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
EOF

cat > "$APP_DIR/proguard-rules.pro" << 'EOF'
# ProGuard rules for Filament
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**
EOF

# ============ Kotlin Source Files ============

mkdir -p "$APP_DIR/src/main/java/com/example/model3dviewer"

# MainActivity.kt
cat > "$APP_DIR/src/main/java/com/example/model3dviewer/MainActivity.kt" << 'EOF'
package com.example.model3dviewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.model3dviewer.adapter.ModelThumbnailAdapter
import com.example.model3dviewer.model.RecentModel
import com.example.model3dviewer.renderer.FilamentRenderer
import com.example.model3dviewer.utils.RecentModelsManager
import com.google.android.filament.utils.Utils
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var surfaceView: SurfaceView
    private lateinit var renderer: FilamentRenderer
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ModelThumbnailAdapter
    private lateinit var recentModelsManager: RecentModelsManager
    
    private var isModelLoaded = false
    
    companion object {
        const val REQUEST_CODE_PICK_MODEL = 1001
        const val GRID_SPAN_COUNT = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Utils.init()
        
        setContentView(R.layout.activity_main)
        setupUI()
        setupRenderer()
        loadRecentModels()
    }
    
    private fun setupUI() {
        surfaceView = findViewById(R.id.surfaceView)
        recyclerView = findViewById(R.id.recyclerView)
        
        recyclerView.layoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)
        adapter = ModelThumbnailAdapter(
            onItemClick = { model -> loadModel(model.path) },
            onItemLongClick = { model -> 
                showModelOptions(model)
                true
            }
        )
        recyclerView.adapter = adapter
        
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            openFilePicker()
        }
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            exitPreviewMode()
        }
        
        findViewById<ImageButton>(R.id.btnReset).setOnClickListener {
            renderer.resetCamera()
        }
        
        setupTouchControls()
    }
    
    private fun setupRenderer() {
        renderer = FilamentRenderer(this, surfaceView)
    }
    
    private fun setupTouchControls() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (isModelLoaded) {
                    renderer.addRotation(distanceY * 0.5f, distanceX * 0.5f)
                    return true
                }
                return false
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                renderer.resetCamera()
                return true
            }
        })
        
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderer.applyZoom(detector.scaleFactor)
                return true
            }
        })
        
        surfaceView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            scaleDetector.onTouchEvent(event)
            true
        }
    }
    
    private fun loadRecentModels() {
        recentModelsManager = RecentModelsManager(this)
        lifecycleScope.launch {
            val models = recentModelsManager.getRecentModels()
            adapter.submitList(models)
            updateEmptyState(models.isEmpty())
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        findViewById<View>(R.id.emptyState).visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "model/gltf-binary",
                "model/gltf+json",
                "model/obj",
                "application/octet-stream"
            ))
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_MODEL)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_MODEL && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                loadModel(uri.toString())
            }
        }
    }
    
    private fun loadModel(path: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                renderer.loadModel(path)
                isModelLoaded = true
                
                val model = RecentModel(
                    id = System.currentTimeMillis(),
                    name = path.substringAfterLast("/"),
                    path = path,
                    lastOpened = System.currentTimeMillis(),
                    polygonCount = 0
                )
                recentModelsManager.addRecentModel(model)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "模型加载成功", Toast.LENGTH_SHORT).show()
                    enterPreviewMode()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun enterPreviewMode() {
        recyclerView.visibility = View.GONE
        findViewById<View>(R.id.emptyState).visibility = View.GONE
        findViewById<View>(R.id.previewControls).visibility = View.VISIBLE
        findViewById<FloatingActionButton>(R.id.fabAdd).hide()
    }
    
    private fun exitPreviewMode() {
        findViewById<View>(R.id.previewControls).visibility = View.GONE
        findViewById<FloatingActionButton>(R.id.fabAdd).show()
        isModelLoaded = false
        loadRecentModels()
    }
    
    private fun showModelOptions(model: RecentModel) {
        val popup = PopupMenu(this, recyclerView.findViewWithTag<View>(model.id))
        popup.menuInflater.inflate(R.menu.model_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete -> {
                    recentModelsManager.removeRecentModel(model)
                    loadRecentModels()
                    true
                }
                R.id.action_share -> {
                    shareModel(model)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
    
    private fun shareModel(model: RecentModel) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(model.path))
        }
        startActivity(Intent.createChooser(intent, "分享模型"))
    }
    
    override fun onResume() {
        super.onResume()
        renderer.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        renderer.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        renderer.destroy()
    }
}
EOF

# FilamentRenderer.kt
mkdir -p "$APP_DIR/src/main/java/com/example/model3dviewer/renderer"
cat > "$APP_DIR/src/main/java/com/example/model3dviewer/renderer/FilamentRenderer.kt" << 'EOF'
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
EOF

# Model Classes
mkdir -p "$APP_DIR/src/main/java/com/example/model3dviewer/model"
cat > "$APP_DIR/src/main/java/com/example/model3dviewer/model/RecentModel.kt" << 'EOF'
package com.example.model3dviewer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_models")
data class RecentModel(
    @PrimaryKey val id: Long,
    val name: String,
    val path: String,
    val thumbnailPath: String? = null,
    val lastOpened: Long,
    val polygonCount: Int,
    val fileSize: Long = 0
)
EOF

# Adapter
mkdir -p "$APP_DIR/src/main/java/com/example/model3dviewer/adapter"
cat > "$APP_DIR/src/main/java/com/example/model3dviewer/adapter/ModelThumbnailAdapter.kt" << 'EOF'
package com.example.model3dviewer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.model3dviewer.R
import com.example.model3dviewer.model.RecentModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ModelThumbnailAdapter(
    private val onItemClick: (RecentModel) -> Unit,
    private val onItemLongClick: (RecentModel) -> Boolean
) : ListAdapter<RecentModel, ModelThumbnailAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_thumbnail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvPolyCount: TextView = itemView.findViewById(R.id.tvPolyCount)

        fun bind(model: RecentModel) {
            itemView.tag = model.id
            tvName.text = model.name
            tvDate.text = formatDate(model.lastOpened)
            tvPolyCount.text = if (model.polygonCount > 0) "${model.polygonCount / 1000}K 面" else "未知"

            if (model.thumbnailPath != null && File(model.thumbnailPath).exists()) {
                Glide.with(itemView.context)
                    .load(model.thumbnailPath)
                    .centerCrop()
                    .placeholder(R.drawable.placeholder_model)
                    .into(ivThumbnail)
            } else {
                Glide.with(itemView.context)
                    .load(R.drawable.placeholder_model)
                    .centerCrop()
                    .into(ivThumbnail)
            }

            itemView.setOnClickListener { onItemClick(model) }
            itemView.setOnLongClickListener { onItemLongClick(model) }
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RecentModel>() {
        override fun areItemsTheSame(oldItem: RecentModel, newItem: RecentModel) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RecentModel, newItem: RecentModel) = oldItem == newItem
    }
}
EOF

# Utils
mkdir -p "$APP_DIR/src/main/java/com/example/model3dviewer/utils"
cat > "$APP_DIR/src/main/java/com/example/model3dviewer/utils/RecentModelsManager.kt" << 'EOF'
package com.example.model3dviewer.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.model3dviewer.model.RecentModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_models")

class RecentModelsManager(private val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val KEY_MODELS = stringPreferencesKey("models_list")
    private val MAX_RECENT = 20

    suspend fun getRecentModels(): List<RecentModel> {
        return try {
            val prefs = context.dataStore.data.first()
            val jsonString = prefs[KEY_MODELS] ?: "[]"
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addRecentModel(model: RecentModel) {
        val current = getRecentModels().toMutableList()
        current.removeAll { it.path == model.path }
        current.add(0, model)
        
        if (current.size > MAX_RECENT) {
            current.subList(MAX_RECENT, current.size).clear()
        }
        
        context.dataStore.edit { prefs ->
            prefs[KEY_MODELS] = json.encodeToString(current)
        }
    }

    suspend fun removeRecentModel(model: RecentModel) {
        val current = getRecentModels().toMutableList()
        current.removeAll { it.id == model.id }
        
        context.dataStore.edit { prefs ->
            prefs[KEY_MODELS] = json.encodeToString(current)
        }
    }
}
EOF

# ============ Layout Files ============

mkdir -p "$APP_DIR/src/main/res/layout"
cat > "$APP_DIR/src/main/res/layout/activity_main.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#1A1A1A">

    <android.opengl.GLSurfaceView
        android:id="@+id/surfaceView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="8dp"
        android:clipToPadding="false"
        android:background="#F5F5F5"/>

    <LinearLayout
        android:id="@+id/emptyState"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:orientation="vertical"
        android:gravity="center"
        android:visibility="gone">

        <ImageView
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:src="@drawable/placeholder_model"
            android:alpha="0.5"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="暂无最近模型"
            android:textSize="18sp"
            android:textColor="#999999"
            android:layout_marginTop="16dp"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="点击右下角按钮添加3D模型"
            android:textSize="14sp"
            android:textColor="#BBBBBB"
            android:layout_marginTop="8dp"/>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/previewControls"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="horizontal"
        android:background="#CC000000"
        android:padding="16dp"
        android:visibility="gone">

        <ImageButton
            android:id="@+id/btnBack"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@drawable/ic_back"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="返回"/>

        <View android:layout_width="0dp" android:layout_height="0dp" android:layout_weight="1"/>

        <ImageButton
            android:id="@+id/btnReset"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@drawable/ic_reset"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="重置"/>
    </LinearLayout>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fabAdd"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="16dp"
        android:src="@drawable/ic_add"
        app:backgroundTint="#4285F4"
        android:contentDescription="添加模型"/>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
EOF

cat > "$APP_DIR/src/main/res/layout/item_model_thumbnail.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="4dp"
    app:cardCornerRadius="8dp"
    app:cardElevation="2dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <ImageView
            android:id="@+id/ivThumbnail"
            android:layout_width="match_parent"
            android:layout_height="120dp"
            android:scaleType="centerCrop"
            android:background="#E0E0E0"/>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="12dp">

            <TextView
                android:id="@+id/tvName"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="14sp"
                android:textColor="#333333"
                android:maxLines="1"
                android:ellipsize="end"
                android:textStyle="bold"/>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="4dp">

                <TextView
                    android:id="@+id/tvPolyCount"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textSize="12sp"
                    android:textColor="#666666"/>

                <View android:layout_width="0dp" android:layout_height="0dp" android:layout_weight="1"/>

                <TextView
                    android:id="@+id/tvDate"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textSize="11sp"
                    android:textColor="#999999"/>
            </LinearLayout>
        </LinearLayout>
    </LinearLayout>
</androidx.cardview.widget.CardView>
EOF

# ============ Resources ============

mkdir -p "$APP_DIR/src/main/res/values"
cat > "$APP_DIR/src/main/res/values/colors.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
    <color name="primary">#4285F4</color>
    <color name="primary_dark">#3367D6</color>
    <color name="accent">#EA4335</color>
</resources>
EOF

cat > "$APP_DIR/src/main/res/values/strings.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">3D Model Viewer</string>
    <string name="action_open">打开模型</string>
    <string name="action_reset">重置视图</string>
    <string name="action_share">分享</string>
    <string name="action_delete">删除</string>
    <string name="empty_state_title">暂无最近模型</string>
    <string name="empty_state_desc">点击右下角按钮添加3D模型</string>
</resources>
EOF

cat > "$APP_DIR/src/main/res/values/themes.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Model3DViewer" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryDark">@color/primary_dark</item>
        <item name="colorAccent">@color/accent</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
EOF

# Menu
mkdir -p "$APP_DIR/src/main/res/menu"
cat > "$APP_DIR/src/main/res/menu/model_options.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/action_share"
        android:title="分享"
        android:orderInCategory="1"/>
    <item
        android:id="@+id/action_delete"
        android:title="删除"
        android:orderInCategory="2"/>
</menu>
EOF

# ============ AndroidManifest ============

cat > "$APP_DIR/src/main/AndroidManifest.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.model3dviewer">

    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
        android:maxSdkVersion="28" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Model3DViewer"
        android:requestLegacyExternalStorage="true">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="fullSensor"
            android:configChanges="orientation|screenSize|smallestScreenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="model/gltf-binary" />
                <data android:mimeType="model/gltf+json" />
                <data android:mimeType="model/obj" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

echo "✅ 项目结构创建完成"

# ============ Python图标生成脚本 ============

echo "🐍 创建Python图标生成脚本..."

cat > "$BASE_DIR/generate_icons.py" << 'EOF'
#!/usr/bin/env python3
import os
import sys
from PIL import Image, ImageDraw, ImageFilter

def create_directory(path):
    os.makedirs(path, exist_ok=True)

def generate_launcher_icon(size, output_path):
    """生成自适应图标"""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    safe_size = int(size * 0.66)
    offset = (size - safe_size) // 2
    
    # 渐变背景圆形
    for i in range(safe_size):
        ratio = i / safe_size
        r = int(66 + (100 - 66) * ratio)
        g = int(133 + (181 - 133) * ratio)
        b = int(244 + (246 - 244) * ratio)
        draw.ellipse(
            [offset + i//2, offset + i//2, 
             size - offset - i//2, size - offset - i//2],
            fill=(r, g, b, 255)
        )
    
    # 3D立方体
    cube_size = int(safe_size * 0.5)
    cube_x = (size - cube_size) // 2
    cube_y = (size - cube_size) // 2
    
    # 前面
    draw.polygon([
        (cube_x, cube_y + cube_size//3),
        (cube_x + cube_size, cube_y + cube_size//3),
        (cube_x + cube_size, cube_y + cube_size),
        (cube_x, cube_y + cube_size)
    ], fill=(255, 255, 255, 230))
    
    # 顶面
    draw.polygon([
        (cube_x + cube_size//4, cube_y),
        (cube_x + cube_size//4 + cube_size, cube_y),
        (cube_x + cube_size, cube_y + cube_size//3),
        (cube_x, cube_y + cube_size//3)
    ], fill=(255, 255, 255, 200))
    
    # 右面
    draw.polygon([
        (cube_x + cube_size//4 + cube_size, cube_y),
        (cube_x + cube_size, cube_y + cube_size//3),
        (cube_x + cube_size, cube_y + cube_size),
        (cube_x + cube_size//4 + cube_size, cube_y + cube_size - cube_size//3)
    ], fill=(255, 255, 255, 180))
    
    # 阴影
    shadow = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.ellipse(
        [offset - 5, size - offset - 10, size - offset + 5, size - offset + 10],
        fill=(0, 0, 0, 50)
    )
    img = Image.alpha_composite(shadow, img)
    img = img.filter(ImageFilter.SMOOTH)
    
    img.save(output_path, 'PNG')
    print(f"✅ 生成: {output_path}")

def generate_simple_icon(size, output_path, color=(66, 133, 244)):
    """生成简单图标"""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    margin = size // 8
    draw.rounded_rectangle(
        [margin, margin, size - margin, size - margin],
        radius=size//4,
        fill=(*color, 255)
    )
    
    # 白色立方体图标
    cube_size = size // 3
    x = (size - cube_size) // 2
    y = (size - cube_size) // 2
    
    draw.polygon([(x, y+cube_size//2), (x+cube_size, y+cube_size//2), 
                  (x+cube_size, y+cube_size), (x, y+cube_size)], 
                 fill=(255, 255, 255, 240))
    draw.polygon([(x+cube_size//4, y), (x+cube_size//4+cube_size, y),
                  (x+cube_size, y+cube_size//2), (x, y+cube_size//2)],
                 fill=(255, 255, 255, 200))
    draw.polygon([(x+cube_size//4+cube_size, y), (x+cube_size+cube_size//4, y+cube_size//2),
                  (x+cube_size, y+cube_size), (x+cube_size, y+cube_size//2)],
                 fill=(255, 255, 255, 160))
    
    img.save(output_path, 'PNG')

def generate_placeholder(size, output_path):
    """生成占位图"""
    img = Image.new('RGB', (size, size), (240, 240, 245))
    draw = ImageDraw.Draw(img)
    
    # 网格
    grid = size // 8
    for i in range(0, size, grid):
        draw.line([(i, 0), (i, size)], fill=(220, 220, 230), width=1)
        draw.line([(0, i), (size, i)], fill=(220, 220, 230), width=1)
    
    # 中心立方体
    cs = size // 4
    x = (size - cs) // 2
    y = (size - cs) // 2
    
    draw.polygon([(x, y+cs//2), (x+cs, y+cs//2), (x+cs, y+cs), (x, y+cs)], 
                 fill=(100, 150, 255))
    draw.polygon([(x+cs//2, y), (x+cs//2+cs, y), (x+cs, y+cs//2), (x, y+cs//2)],
                 fill=(150, 180, 255))
    draw.polygon([(x+cs//2+cs, y), (x+cs+cs//2, y+cs//2), (x+cs, y+cs), (x+cs, y+cs//2)],
                 fill=(80, 120, 220))
    
    img.save(output_path, 'PNG')

def main():
    base = "app/src/main/res"
    
    # 应用图标尺寸
    sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }
    
    for folder, size in sizes.items():
        path = f"{base}/{folder}"
        create_directory(path)
        generate_launcher_icon(size, f"{path}/ic_launcher.png")
        generate_launcher_icon(size, f"{path}/ic_launcher_round.png")
        generate_launcher_icon(size, f"{path}/ic_launcher_foreground.png")
        generate_simple_icon(size, f"{path}/ic_launcher_background.png", (255, 255, 255))
    
    # Drawable资源
    dpath = f"{base}/drawable"
    create_directory(dpath)
    generate_placeholder(512, f"{dpath}/placeholder_model.png")
    
    # 简单图标
    for name in ['ic_add', 'ic_back', 'ic_reset', 'ic_info']:
        generate_simple_icon(96, f"{dpath}/{name}.png", (255, 255, 255))
    
    print("\n🎨 所有图标生成完成！")

if __name__ == "__main__":
    main()
EOF

chmod +x "$BASE_DIR/generate_icons.py"

# ============ 执行Python脚本生成图标 ============

echo "🎨 生成图标文件..."
cd "$BASE_DIR"
if command -v python3 &> /dev/null; then
    python3 generate_icons.py || echo "⚠️  Python脚本执行失败，请手动安装Pillow后运行: pip install pillow"
elif command -v python &> /dev/null; then
    python generate_icons.py || echo "⚠️  Python脚本执行失败，请手动安装Pillow后运行: pip install pillow"
else
    echo "⚠️  未找到Python，跳过图标生成。请手动安装Python和Pillow后运行 generate_icons.py"
fi

# ============ 创建Gradle Wrapper脚本 ============

cat > "$BASE_DIR/gradlew" << 'EOF'
#!/bin/sh
##############################################################################
##
##  Gradle start up script for POSIX generated by Gradle.
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" -a "$nonstop" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $? -eq 0 ] ; then
        if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
            MAX_FD="$MAX_FD_LIMIT"
        fi
        ulimit -n $MAX_FD
        if [ $? -ne 0 ] ; then
            warn "Could not set maximum file descriptor limit: $MAX_FD"
        fi
    else
        warn "Could not query maximum file descriptor limit: $MAX_FD_LIMIT"
    fi
fi

# For Darwin, add options to specify how the application appears in the dock
if [ "$darwin" = "true" ]; then
    GRADLE_OPTS="$GRADLE_OPTS \"-Xdock:name=$APP_NAME\" \"-Xdock:icon=$APP_HOME/media/gradle.icns\""
fi

# For Cygwin or MSYS, switch paths to Windows format before running java
if [ "$cygwin" = "true" -o "$msys" = "true" ] ; then
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
    
    JAVACMD=`cygpath --unix "$JAVACMD"`

    # We build the pattern for arguments to be converted via cygpath
    ROOTDIRSRAW=`find -L / -maxdepth 1 -mindepth 1 -type d 2>/dev/null`
    SEP=""
    for dir in $ROOTDIRSRAW ; do
        ROOTDIRS="$ROOTDIRS$SEP$dir"
        SEP="|"
    done
    OURCYGPATTERN="(^($ROOTDIRS))"
    # Add a user-defined pattern to the cygpath arguments
    if [ "$GRADLE_CYGPATTERN" != "" ] ; then
        OURCYGPATTERN="$OURCYGPATTERN|($GRADLE_CYGPATTERN)"
    fi
    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    i=0
    for arg in "$@" ; do
        CHECK=`echo "$arg"|egrep -c "$OURCYGPATTERN" -`
        CHECK2=`echo "$arg"|egrep -c "^-"`                                 ### Determine if an option

        if [ $CHECK -ne 0 ] && [ $CHECK2 -eq 0 ] ; then                    ### Added a condition
            eval `echo args$i`=`cygpath --path --ignore --mixed "$arg"`
        else
            eval `echo args$i`="\"$arg\""
        fi
        i=`expr $i + 1`
    done
    case $i in
        0) set -- ;;
        1) set -- "$args0" ;;
        2) set -- "$args0" "$args1" ;;
        3) set -- "$args0" "$args1" "$args2" ;;
        4) set -- "$args0" "$args1" "$args2" "$args3" ;;
        5) set -- "$args0" "$args1" "$args2" "$args3" "$args4" ;;
        6) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" ;;
        7) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" "$args6" ;;
        8) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" "$args6" "$args7" ;;
        9) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" "$args6" "$args7" "$args8" ;;
    esac
fi

# Escape application args
save () {
    for i do printf %s\\n "$i" | sed "s/'/'\\\\''/g;1s/^/'/;\$s/\$/' \\\\/" ; done
    echo " "
}
APP_ARGS=`save "$@"`

# Collect all arguments for the java command
set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "\"-Dorg.gradle.appname=$APP_BASE_NAME\"" -classpath "\"$CLASSPATH\"" org.gradle.wrapper.GradleWrapperMain "$APP_ARGS"

exec "$JAVACMD" "$@"
EOF

chmod +x "$BASE_DIR/gradlew"

# Windows gradlew.bat
cat > "$BASE_DIR/gradlew.bat" << 'EOF'
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
EOF

echo ""
echo "✅ 项目创建完成！"
echo "📁 项目位置: $BASE_DIR"
echo ""
echo "📋 项目特性："
echo "  • Google Filament PBR渲染引擎"
echo "  • 缩略图预览 (RecyclerView)"
echo "  • 动态LOD系统"
echo "  • 视锥体剔除优化"
echo "  • GLTF/OBJ格式支持"
echo "  • 动画播放支持"
echo ""
echo "🚀 使用步骤："
echo "  1. cd $PROJECT_NAME"
echo "  2. ./gradlew assembleDebug"
echo ""
echo "🎨 如果图标未生成，请手动运行："
echo "  pip install pillow"
echo "  python3 generate_icons.py"
echo ""

