package com.example.model3dviewer

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var surfaceView: SurfaceView
    private lateinit var renderer: FilamentRenderer
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ModelThumbnailAdapter
    private lateinit var recentModelsManager: RecentModelsManager
    
    private var isModelLoaded = false
    private var loadJob: Job? = null
    private var progressDialog: ProgressDialog? = null
    
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
            e.printStackTrace()
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
            try {
                val models = recentModelsManager.getRecentModels()
                adapter.submitList(models)
                updateEmptyState(models.isEmpty())
            } catch (e: Exception) {
                e.printStackTrace()
                updateEmptyState(true)
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        findViewById<View>(R.id.emptyState).visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun openFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "model/gltf-binary",
                    "model/gltf+json",
                    "application/octet-stream"
                ))
            }
            startActivityForResult(intent, REQUEST_CODE_PICK_MODEL)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "无法打开文件选择器: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_MODEL && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                loadModel(uri.toString())
            }
        }
    }
    
    private fun loadModel(path: String) {
        cancelLoad()
        
        progressDialog = ProgressDialog(this).apply {
            setMessage("加载模型中...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(true)
            setButton(ProgressDialog.BUTTON_NEGATIVE, "取消") { _, _ ->
                cancelLoad()
            }
            show()
        }
        
        loadJob = lifecycleScope.launch(Dispatchers.IO) {
            val startTime = SystemClock.elapsedRealtime()
            
            try {
                val success = renderer.loadModel(path) { progress ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        progressDialog?.progress = progress
                    }
                }
                
                val loadTime = SystemClock.elapsedRealtime() - startTime
                
                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()
                    progressDialog = null
                    
                    if (success) {
                        isModelLoaded = true
                        
                        val model = RecentModel(
                            id = System.currentTimeMillis(),
                            name = path.substringAfterLast("/").substringBefore("?"),
                            path = path,
                            lastOpened = System.currentTimeMillis(),
                            polygonCount = 0
                        )
                        
                        lifecycleScope.launch {
                            recentModelsManager.addRecentModel(model)
                        }
                        
                        Toast.makeText(this@MainActivity, 
                            "加载成功 (${loadTime}ms)", Toast.LENGTH_SHORT).show()
                        enterPreviewMode()
                    } else {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("加载失败")
                            .setMessage("无法加载模型文件，请确保选择有效的GLB/GLTF文件")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                
                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()
                    progressDialog = null
                    
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("加载错误")
                        .setMessage("错误: ${e.message}\n\n建议:\n1. 尝试更小的模型文件\n2. 检查文件是否损坏\n3. 重启应用后重试")
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    private fun cancelLoad() {
        loadJob?.cancel()
        loadJob = null
        progressDialog?.dismiss()
        progressDialog = null
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
        val view = recyclerView.findViewWithTag<View>(model.id)
        if (view == null) {
            lifecycleScope.launch {
                recentModelsManager.removeRecentModel(model)
                loadRecentModels()
            }
            return
        }
        
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.model_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete -> {
                    lifecycleScope.launch {
                        recentModelsManager.removeRecentModel(model)
                        loadRecentModels()
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
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(model.path))
            }
            startActivity(Intent.createChooser(intent, "分享模型"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            renderer.onResume()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onPause() {
        super.onPause()
        cancelLoad()
        try {
            renderer.onPause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cancelLoad()
        try {
            renderer.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

