package com.example.model3dviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.model3dviewer.adapter.ModelThumbnailAdapter
import com.example.model3dviewer.model.RecentModel
import com.example.model3dviewer.renderer.FilamentRenderer
import com.example.model3dviewer.utils.RecentModelsManager
import com.google.android.filament.utils.Utils
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.*
import java.net.URLDecoder
import java.util.UUID
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    
    private lateinit var surfaceView: SurfaceView
    private lateinit var renderer: FilamentRenderer
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ModelThumbnailAdapter
    private lateinit var recentModelsManager: RecentModelsManager
    
    private var isModelLoaded = false
    private var loadJob: Job? = null
    
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var progressContainer: View
    
    companion object {
        const val GRID_SPAN_COUNT = 3
    }

    private val pickModelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { 
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(it, flags)
                loadModel(it.toString())
            } catch (e: SecurityException) {
                Toast.makeText(this, "无法获取文件权限", Toast.LENGTH_LONG).show()
            }
        }
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
        progressIndicator = findViewById(R.id.progressIndicator)
        progressText = findViewById(R.id.progressText)
        progressContainer = findViewById(R.id.progressContainer)
        
        recyclerView.layoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)
        adapter = ModelThumbnailAdapter(
            onItemClick = { model -> loadModel(model.path) },
            onItemLongClick = { model, view -> 
                showModelOptions(model, view)
                true
            }
        )
        recyclerView.adapter = adapter
        
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            openFilePicker()
        }
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            cancelLoad()
            exitPreviewMode()
        }
        
        findViewById<ImageButton>(R.id.btnReset).setOnClickListener {
            renderer.resetCamera()
        }
        
        setupTouchControls()
    }
    
    private fun setupRenderer() {
        try {
            renderer = FilamentRenderer(this, surfaceView)
        } catch (e: Exception) {
            Toast.makeText(this, "初始化渲染器失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
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
                if (isModelLoaded) {
                    renderer.resetCamera()
                    return true
                }
                return false
            }
        })
        
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isModelLoaded) {
                    renderer.applyZoom(detector.scaleFactor)
                    return true
                }
                return false
            }
        })
        
        surfaceView.setOnTouchListener { _, event ->
            if (!isModelLoaded) return@setOnTouchListener false
            val handled = gestureDetector.onTouchEvent(event)
            scaleDetector.onTouchEvent(event) || handled
        }
    }
    
    private fun loadRecentModels() {
        recentModelsManager = RecentModelsManager(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val models = recentModelsManager.getRecentModels()
                    adapter.submitList(models)
                    updateEmptyState(models.isEmpty())
                } catch (e: Exception) {
                    updateEmptyState(true)
                }
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        findViewById<View>(R.id.emptyState).isVisible = isEmpty
        recyclerView.isVisible = !isEmpty
    }
    
    private fun openFilePicker() {
        try {
            pickModelLauncher.launch(arrayOf(
                "model/gltf-binary",
                "model/gltf+json",
                "application/octet-stream",
                "*/*"
            ))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件选择器: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun loadModel(path: String) {
        cancelLoad()
        showProgress()
        
        loadJob = lifecycleScope.launch {
            val startTime = SystemClock.elapsedRealtime()
            
            // 使用 runCatching 改进错误处理
            val result = runCatching {
                renderer.loadModel(path) { progress ->
                    launch(Dispatchers.Main) {
                        progressIndicator.progress = progress
                        progressText.text = "加载中... $progress%"
                    }
                }
            }
            
            val loadTime = SystemClock.elapsedRealtime() - startTime
            hideProgress()
            
            when {
                result.isSuccess && result.getOrDefault(false) -> {
                    isModelLoaded = true
                    val model = createRecentModel(path)
                    
                    lifecycleScope.launch {
                        recentModelsManager.addRecentModel(model)
                    }
                    
                    Toast.makeText(this@MainActivity, "加载成功 (${loadTime}ms)", Toast.LENGTH_SHORT).show()
                    enterPreviewMode()
                }
                else -> {
                    val errorMsg = result.exceptionOrNull()?.message ?: "未知错误"
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("加载失败")
                        .setMessage("无法加载模型: $errorMsg")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    private fun createRecentModel(path: String): RecentModel {
        val uri = Uri.parse(path)
        val encodedName = uri.lastPathSegment ?: "未知模型"
        val name = try {
            URLDecoder.decode(encodedName, "UTF-8")
        } catch (e: Exception) {
            encodedName
        }
        
        return RecentModel(
            id = "${System.currentTimeMillis()}_${Random.nextInt(1000)}_${UUID.randomUUID().toString().take(8)}",
            name = name,
            path = path,
            lastOpened = System.currentTimeMillis(),
            polygonCount = 0
        )
    }
    
    private fun showProgress() {
        progressContainer.isVisible = true
        progressIndicator.progress = 0
        progressText.text = "加载中... 0%"
    }
    
    private fun hideProgress() {
        progressContainer.isVisible = false
    }
    
    private fun cancelLoad() {
        loadJob?.cancel()
        loadJob = null
        hideProgress()
    }
    
    private fun enterPreviewMode() {
        recyclerView.isVisible = false
        findViewById<View>(R.id.emptyState).isVisible = false
        findViewById<View>(R.id.previewControls).isVisible = true
        findViewById<FloatingActionButton>(R.id.fabAdd).hide()
    }
    
    private fun exitPreviewMode() {
        findViewById<View>(R.id.previewControls).isVisible = false
        findViewById<FloatingActionButton>(R.id.fabAdd).show()
        isModelLoaded = false
        lifecycleScope.launch {
            val models = recentModelsManager.getRecentModels()
            adapter.submitList(models)
            updateEmptyState(models.isEmpty())
        }
    }
    
    private fun showModelOptions(model: RecentModel, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.model_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete -> {
                    lifecycleScope.launch {
                        recentModelsManager.removeRecentModel(model)
                        val models = recentModelsManager.getRecentModels()
                        adapter.submitList(models)
                        updateEmptyState(models.isEmpty())
                    }
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
        try {
            val uri = Uri.parse(model.path)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享模型"))
        } catch (e: Exception) {
            Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isModelLoaded) {
            exitPreviewMode()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onResume() {
        super.onResume()
        renderer.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        cancelLoad()
        renderer.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cancelLoad()
        renderer.destroy()
    }
}

