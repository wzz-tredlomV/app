package com.qrfiletransfer.receiver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.qrfiletransfer.R
import com.qrfiletransfer.databinding.ActivityReceiverBinding
import com.qrfiletransfer.model.FileChunk
import com.qrfiletransfer.model.TransferSession
import com.qrfiletransfer.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class ReceiverActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityReceiverBinding
    
    private lateinit var qrScannerManager: QRScannerManager
    private lateinit var fileDecoder: FileDecoder
    private lateinit var resumeManager: ResumeManager
    private lateinit var errorRecovery: ErrorRecovery
    
    private var isScanning = false
    private var currentSessionId: String? = null
    
    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startScanning()
        } else {
            showPermissionDeniedDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initViews()
        initManagers()
        checkCameraPermission()
    }
    
    override fun onResume() {
        super.onResume()
        if (isScanning) {
            qrScannerManager.resumeScanning()
        }
    }
    
    override fun onPause() {
        super.onPause()
        qrScannerManager.pauseScanning()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        qrScannerManager.stopScanning()
    }
    
    private fun initViews() {
        supportActionBar?.title = "接收文件"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        binding.btnStartScan.setOnClickListener {
            if (isScanning) {
                stopScanning()
            } else {
                startScanning()
            }
        }
        
        binding.btnSaveFile.setOnClickListener {
            saveCurrentFile()
        }
        
        binding.btnResume.setOnClickListener {
            showResumeDialog()
        }
        
        updateUIState(UIState.IDLE)
    }
    
    private fun initManagers() {
        qrScannerManager = QRScannerManager(this, lifecycle)
        fileDecoder = FileDecoder(this)
        resumeManager = ResumeManager(this)
        errorRecovery = ErrorRecovery()
        
        qrScannerManager.setCallbacks(
            onQRCodeScanned = { qrData ->
                processQRCodeData(qrData)
            },
            onError = { error ->
                runOnUiThread {
                    showError("扫描失败: ${error.message}")
                }
            }
        )
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // 权限已授予
            }
            
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationaleDialog()
            }
            
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要相机权限")
            .setMessage("应用需要相机权限来扫描二维码。请授予相机权限以继续。")
            .setPositiveButton("授予权限") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("取消") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("权限被拒绝")
            .setMessage("相机权限被拒绝，无法扫描二维码。请在设置中启用相机权限。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("取消") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun startScanning() {
        if (!isScanning) {
            isScanning = true
            updateUIState(UIState.SCANNING)
            qrScannerManager.startScanning()
        }
    }
    
    private fun stopScanning() {
        if (isScanning) {
            isScanning = false
            updateUIState(UIState.IDLE)
            qrScannerManager.stopScanning()
        }
    }
    
    private fun processQRCodeData(qrData: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val chunk = FileChunk.fromJson(qrData)
                
                val result = fileDecoder.processChunk(chunk)
                
                withContext(Dispatchers.Main) {
                    handleProcessResult(result, chunk)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReceiverActivity, 
                        "二维码解析失败: ${e.message}", 
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handleProcessResult(result: FileDecoder.ProcessResult, chunk: FileChunk) {
        when (result) {
            is FileDecoder.ProcessResult.Progress -> {
                updateProgress(result.received, result.total)
                updateChunkInfo(chunk)
                
                currentSessionId = chunk.sessionId
                saveSessionState(chunk, result)
            }
            
            is FileDecoder.ProcessResult.Success -> {
                updateUIState(UIState.COMPLETED)
                showFileReceivedDialog(result)
                
                currentSessionId = null
            }
            
            is FileDecoder.ProcessResult.Error -> {
                showErrorDialog(result.message)
            }
            
            is FileDecoder.ProcessResult.Duplicate -> {
                Toast.makeText(this, "重复块: ${result.chunkIndex}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun saveSessionState(chunk: FileChunk, result: FileDecoder.ProcessResult) {
        lifecycleScope.launch(Dispatchers.IO) {
            resumeManager.saveChunk(chunk)
        }
    }
    
    private fun showResumeDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sessions = resumeManager.getResumableSessions()
            
            withContext(Dispatchers.Main) {
                if (sessions.isEmpty()) {
                    Toast.makeText(this@ReceiverActivity, 
                        "没有可恢复的传输", 
                        Toast.LENGTH_SHORT).show()
                } else {
                    showSessionListDialog(sessions)
                }
            }
        }
    }
    
    private fun showSessionListDialog(sessions: List<TransferSession>) {
        val items = sessions.map { session ->
            "${session.fileName} (${session.chunksReceived}/${session.totalChunks})"
        }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("选择要恢复的传输")
            .setItems(items) { _, which ->
                val session = sessions[which]
                resumeTransfer(session)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun resumeTransfer(session: TransferSession) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = resumeManager.resumeTransfer(session.id)
            
            withContext(Dispatchers.Main) {
                when (result) {
                    is ResumeManager.ResumeResult.Success -> {
                        currentSessionId = session.id
                        updateProgress(result.progress.first, result.progress.second)
                        updateUIState(UIState.SCANNING)
                        
                        Toast.makeText(this@ReceiverActivity,
                            "已恢复传输: ${session.fileName}",
                            Toast.LENGTH_SHORT).show()
                    }
                    
                    is ResumeManager.ResumeResult.Error -> {
                        Toast.makeText(this@ReceiverActivity,
                            "恢复失败: ${result.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun saveCurrentFile() {
        Toast.makeText(this, "保存文件功能", Toast.LENGTH_SHORT).show()
    }
    
    private fun showFileReceivedDialog(result: FileDecoder.ProcessResult.Success) {
        AlertDialog.Builder(this)
            .setTitle("文件接收完成")
            .setMessage(
                "文件: ${result.file.name}\n" +
                "大小: ${formatFileSize(result.fileSize)}\n" +
                "哈希: ${result.fileHash.take(16)}...\n" +
                "块数: ${result.chunksProcessed}\n" +
                "压缩: ${if (result.compressionUsed) "是" else "否"}\n" +
                "纠错: ${if (result.errorRecoveryUsed) "是" else "否"}"
            )
            .setPositiveButton("打开文件") { _, _ ->
                openFile(result.file)
            }
            .setNegativeButton("确定", null)
            .show()
    }
    
    private fun openFile(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        
        intent.setDataAndType(uri, getMimeType(file))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("错误")
            .setMessage(message)
            .setPositiveButton("重试", null)
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun updateProgress(received: Int, total: Int) {
        binding.progressBar.max = total
        binding.progressBar.progress = received
        
        binding.tvProgress.text = "$received/$total (${(received.toDouble() / total * 100).toInt()}%)"
        binding.tvChunksReceived.text = "已接收块: $received/$total"
    }
    
    private fun updateChunkInfo(chunk: FileChunk) {
        binding.tvFileInfo.text = """
            文件: ${chunk.fileName}
            当前块: ${chunk.chunkIndex + 1}/${chunk.totalChunks}
            哈希: ${chunk.dataHash.take(8)}...
        """.trimIndent()
    }
    
    private fun updateUIState(state: UIState) {
        when (state) {
            UIState.IDLE -> {
                binding.btnStartScan.text = "开始扫描"
                binding.btnStartScan.isEnabled = true
                binding.btnSaveFile.isEnabled = false
                binding.btnResume.isEnabled = true
                binding.tvScanStatus.text = "准备扫描"
                binding.tvScanStatus.setTextColor(ContextCompat.getColor(this, R.color.gray))
            }
            
            UIState.SCANNING -> {
                binding.btnStartScan.text = "停止扫描"
                binding.btnStartScan.isEnabled = true
                binding.btnSaveFile.isEnabled = false
                binding.btnResume.isEnabled = false
                binding.tvScanStatus.text = "正在扫描..."
                binding.tvScanStatus.setTextColor(ContextCompat.getColor(this, R.color.blue))
            }
            
            UIState.COMPLETED -> {
                binding.btnStartScan.text = "重新扫描"
                binding.btnStartScan.isEnabled = true
                binding.btnSaveFile.isEnabled = true
                binding.btnResume.isEnabled = false
                binding.tvScanStatus.text = "传输完成"
                binding.tvScanStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
            }
        }
    }
    
    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            size >= 1024 -> String.format("%.2f KB", size / 1024.0)
            else -> "$size B"
        }
    }
    
    enum class UIState {
        IDLE,
        SCANNING,
        COMPLETED
    }
}
