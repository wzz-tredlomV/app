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
