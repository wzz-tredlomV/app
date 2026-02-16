package com.example.model3dviewer

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
        const val REQUEST_CODE_PERMISSIONS = 1002
        const val GRID_SPAN_COUNT = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Utils.init()
        
        setContentView(R.layout.activity_main)
        
        checkPermissions()
        setupUI()
        setupRenderer()
        loadRecentModels()
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用MANAGE_EXTERNAL_STORAGE或SAF
            return
        }
        
        val permissions = arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needPermissions.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }
    
    private fun setupUI() {
        surfaceView = findViewById(R.id.surfaceView)
        recyclerView = findViewById(R.id.recyclerView)
        
        recyclerView.layoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)
        adapter = ModelThumbnailAdapter(
            onItemClick = { model -> loadModel(model.path) },
            onItemLongClick = { model -> 
                lifecycleScope.launch {
                    showModelOptions(model)
                }
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
                // 持久化URI权限
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                loadModel(uri.toString())
            }
        }
    }
    
    private fun loadModel(path: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "正在加载模型...", Toast.LENGTH_SHORT).show()
                }
                
                renderer.loadModel(path)
                isModelLoaded = true
                
                val model = RecentModel(
                    id = System.currentTimeMillis(),
                    name = path.substringAfterLast("/").substringBefore("?"),
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
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("加载失败")
                        .setMessage("错误: ${e.message}\n\n请确保选择有效的GLB/GLTF文件")
                        .setPositiveButton("确定", null)
                        .show()
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
    
    private suspend fun showModelOptions(model: RecentModel) {
        val popup = PopupMenu(this, recyclerView.findViewWithTag<View>(model.id) ?: return)
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
        try {
            renderer.onPause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            renderer.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
