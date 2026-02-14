package com.example.unarchiver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var tvSelectedFiles: TextView
    private lateinit var tvSelectedDest: TextView
    private lateinit var btnPickFiles: Button
    private lateinit var btnPickDest: Button
    private lateinit var btnCompress: Button
    private lateinit var btnExtract: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvCurrentFile: TextView
    private lateinit var spinnerFormat: Spinner
    private lateinit var etOutputName: EditText

    private var selectedUris: List<Uri> = emptyList()
    private var selectedDestTreeUri: Uri? = null

    private val pickFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            selectedUris = uris
            tvSelectedFiles.text = uris.joinToString("\n") { it.lastPathSegment ?: it.toString() }
            uris.forEach { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        } else {
            selectedUris = emptyList()
            tvSelectedFiles.text = getString(R.string.no_file_selected)
        }
        updateButtons()
    }

    private val pickDestLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            selectedDestTreeUri = uri
            tvSelectedDest.text = uri.path ?: uri.toString()
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } else {
            selectedDestTreeUri = null
            tvSelectedDest.text = getString(R.string.no_destination_selected)
        }
        updateButtons()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSelectedFiles = findViewById(R.id.tv_selected_files)
        tvSelectedDest = findViewById(R.id.tv_selected_dest)
        btnPickFiles = findViewById(R.id.btn_pick_files)
        btnPickDest = findViewById(R.id.btn_pick_dest)
        btnCompress = findViewById(R.id.btn_compress)
        btnExtract = findViewById(R.id.btn_extract)
        progressBar = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tv_status)
        tvCurrentFile = findViewById(R.id.tv_current_file)
        spinnerFormat = findViewById(R.id.spinner_format)
        etOutputName = findViewById(R.id.et_output_name)

        btnPickFiles.setOnClickListener { pickFilesLauncher.launch(arrayOf("*/*")) }
        btnPickDest.setOnClickListener { pickDestLauncher.launch(null) }

        val formats = listOf("zip", "7z", "tar", "tar.gz", "tar.bz2", "tar.xz", "rar")
        spinnerFormat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formats)

        btnCompress.setOnClickListener { startCompress() }
        btnExtract.setOnClickListener { startExtract() }

        updateButtons()
    }

    private fun updateButtons() {
        val hasSelection = selectedUris.isNotEmpty()
        val hasDest = selectedDestTreeUri != null
        btnCompress.isEnabled = hasSelection && hasDest && etOutputName.text.isNotBlank()
        btnExtract.isEnabled = selectedUris.size == 1 && hasDest
    }

    private fun startCompress() {
        val destTree = selectedDestTreeUri ?: return
        val format = spinnerFormat.selectedItem as String
        val outputName = etOutputName.text.toString().trim()
        if (outputName.isEmpty()) {
            toast("请填写输出文件名")
            return
        }

        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        tvStatus.text = "开始压缩..."
        tvCurrentFile.text = ""
        setUiEnabled(false)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val notificationHelper = NotificationHelper(this@MainActivity)
                    val manager = ArchiveManager(this@MainActivity, cacheDir, object : ProgressCallback {
                        override fun onFileStart(name: String, totalBytes: Long?) {
                            notificationHelper.notifyFileStart(name, totalBytes)
                            runOnUiThread { tvCurrentFile.text = name }
                        }
                        override fun onFileProgress(name: String, readBytes: Long, totalBytes: Long?) {
                            val percent = if (totalBytes != null && totalBytes > 0) ((readBytes * 100 / totalBytes).toInt()) else 0
                            notificationHelper.notifyFileProgress(name, percent, readBytes, totalBytes)
                            runOnUiThread { progressBar.progress = percent }
                        }
                        override fun onFileComplete(name: String) {
                            notificationHelper.notifyFileComplete(name)
                        }
                        override fun onOverallProgress(percent: Int) {
                            notificationHelper.notifyOverallProgress(percent)
                            runOnUiThread { progressBar.progress = percent }
                        }
                    })
                    val fileName = when {
                        format == "tar.gz" -> "$outputName.tar.gz"
                        format == "tar.bz2" -> "$outputName.tar.bz2"
                        format == "tar.xz" -> "$outputName.tar.xz"
                        format == "tar" -> "$outputName.tar"
                        else -> "$outputName.$format"
                    }
                    manager.compressUrisToDocumentTree(selectedUris, destTree, fileName, format)
                    "压缩完成: $fileName"
                } catch (e: Exception) {
                    "压缩失败: ${e.message}"
                }
            }
            progressBar.visibility = View.GONE
            tvStatus.text = result
            tvCurrentFile.text = ""
            setUiEnabled(true)
        }
    }

    private fun startExtract() {
        if (selectedUris.size != 1) {
            toast("请仅选择一个压缩包以解压")
            return
        }
        val srcUri = selectedUris[0]
        val destTree = selectedDestTreeUri ?: return

        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        tvStatus.text = "开始解压..."
        tvCurrentFile.text = ""
        setUiEnabled(false)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val notificationHelper = NotificationHelper(this@MainActivity)
                    val manager = ArchiveManager(this@MainActivity, cacheDir, object : ProgressCallback {
                        override fun onFileStart(name: String, totalBytes: Long?) {
                            notificationHelper.notifyFileStart(name, totalBytes)
                            runOnUiThread { tvCurrentFile.text = name }
                        }
                        override fun onFileProgress(name: String, readBytes: Long, totalBytes: Long?) {
                            val percent = if (totalBytes != null && totalBytes > 0) ((readBytes * 100 / totalBytes).toInt()) else 0
                            notificationHelper.notifyFileProgress(name, percent, readBytes, totalBytes)
                            runOnUiThread { progressBar.progress = percent }
                        }
                        override fun onFileComplete(name: String) {
                            notificationHelper.notifyFileComplete(name)
                        }
                        override fun onOverallProgress(percent: Int) {
                            notificationHelper.notifyOverallProgress(percent)
                            runOnUiThread { progressBar.progress = percent }
                        }
                    })
                    val out = manager.extractUriToDocumentTree(srcUri, destTree)
                    "解压完成: $out"
                } catch (e: Exception) {
                    "解压失败: ${e.message}"
                }
            }
            progressBar.visibility = View.GONE
            tvStatus.text = result
            tvCurrentFile.text = ""
            setUiEnabled(true)
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        btnPickFiles.isEnabled = enabled
        btnPickDest.isEnabled = enabled
        btnCompress.isEnabled = enabled
        btnExtract.isEnabled = enabled
    }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }
}
