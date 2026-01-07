package com.qrfiletransfer.sender

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.qrfiletransfer.R
import com.qrfiletransfer.databinding.ActivitySenderBinding
import com.qrfiletransfer.model.FileChunk
import com.qrfiletransfer.model.QRConfig
import com.qrfiletransfer.model.TransferSession
import com.qrfiletransfer.utils.QRVersionCalculator
import com.qrfiletransfer.utils.StorageUtils
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class SenderActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySenderBinding
    
    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startFilePicker()
        } else {
            showPermissionDeniedDialog()
        }
    }
    
    // 文件选择
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            handleFileSelection(selectedUri)
        }
    }
    
    // 管理器
    private lateinit var fileEncoder: FileEncoder
    private lateinit var qrGeneratorManager: QRGeneratorManager
    private lateinit var compressionManager: CompressionManager
    
    // 状态
    private var selectedFile: File? = null
    private var currentSession: TransferSession? = null
    private var fileChunks: List<FileChunk> = emptyList()
    private var qrConfig = QRConfig.getDefault()
    
    // UI更新
    private val mainHandler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySenderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initViews()
        initManagers()
        checkPermissions()
    }
    
    override fun onResume() {
        super.onResume()
        // 恢复二维码生成
        qrGeneratorManager.resumeGeneration()
    }
    
    override fun onPause() {
        super.onPause()
        // 暂停二维码生成
        qrGeneratorManager.pauseGeneration()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        qrGeneratorManager.stopGeneration()
    }
    
    private fun initViews() {
        // 设置标题
        supportActionBar?.title = "发送文件"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 选择文件按钮
        binding.btnSelectFile.setOnClickListener {
            checkPermissions()
        }
        
        // 开始传输按钮
        binding.btnStartTransfer.setOnClickListener {
            startFileTransfer()
        }
        
        // 暂停/继续按钮
        binding.btnPauseResume.setOnClickListener {
            togglePauseResume()
        }
        
        // 停止按钮
        binding.btnStop.setOnClickListener {
            stopTransfer()
        }
        
        // 配置按钮
        binding.btnConfig.setOnClickListener {
            showConfigurationDialog()
        }
        
        // 初始状态
        updateUIState(UIState.SELECT_FILE)
    }
    
    private fun initManagers() {
        fileEncoder = FileEncoder(this)
        qrGeneratorManager = QRGeneratorManager(this, lifecycle)
        compressionManager = CompressionManager(this)
        
        // 设置QR生成回调
        qrGeneratorManager.setCallbacks(
            onQRGenerated = { bitmap, chunk, index ->
                runOnUiThread {
                    binding.ivQrCode.setImageBitmap(bitmap)
                    updateChunkInfo(chunk, index)
                }
            },
            onProgressUpdate = { current, total, displayTime ->
                runOnUiThread {
                    updateProgress(current, total, displayTime)
                }
            },
            onCompletion = {
                runOnUiThread {
                    onTransferComplete()
                }
            },
            onError = { error ->
                runOnUiThread {
                    showError("二维码生成失败: ${error.message}")
                }
            }
        )
    }
    
    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        when {
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                startFilePicker()
            }
            
            shouldShowRequestPermissionRationale(permission) -> {
                showPermissionRationaleDialog()
            }
            
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }
    
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要存储权限")
            .setMessage("应用需要存储权限来读取文件。请授予存储权限以继续。")
            .setPositiveButton("授予权限") { _, _ ->
                requestPermissionLauncher.launch(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                )
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
            .setMessage("存储权限被拒绝，无法读取文件。请在设置中启用存储权限。")
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
    
    private fun startFilePicker() {
        pickFileLauncher.launch("*/*")
    }
    
    private fun handleFileSelection(uri: Uri) {
        try {
            val filePath = getFilePathFromUri(uri)
            selectedFile = File(filePath)
            
            if (selectedFile?.exists() == true) {
                displayFileInfo(selectedFile!!)
                updateUIState(UIState.FILE_SELECTED)
            } else {
                showError("文件不存在")
            }
        } catch (e: Exception) {
            showError("无法读取文件: ${e.message}")
        }
    }
    
    private fun getFilePathFromUri(uri: Uri): String {
        // 简化实现，实际项目中需要处理各种URI
        return uri.path ?: throw IllegalArgumentException("无效的URI")
    }
    
    private fun displayFileInfo(file: File) {
        binding.tvFileName.text = "文件名: ${file.name}"
        binding.tvFileSize.text = "大小: ${StorageUtils.formatFileSize(file.length())}"
        binding.tvFilePath.text = "路径: ${file.path}"
        
        // 估算传输信息
        val estimatedConfig = QRVersionCalculator.calculateOptimalConfig(
            dataSize = file.length().toInt(),
            preferredErrorLevel = qrConfig.errorCorrectionLevel
        )
        
        binding.tvEstimatedInfo.text = """
            预估信息:
            二维码版本: ${estimatedConfig.version}
            纠错级别: ${estimatedConfig.errorCorrectionLevel}
            预估块数: ${estimatedConfig.optimalChunkSize}
            推荐压缩: ${if (estimatedConfig.compressionRecommended) "是" else "否"}
        """.trimIndent()
    }
    
    private fun startFileTransfer() {
        selectedFile?.let { file ->
            if (!file.exists()) {
                showError("文件不存在")
                return
            }
            
            // 检查存储空间
            if (!StorageUtils.hasEnoughSpace(file.parent, file.length())) {
                showError("存储空间不足")
                return
            }
            
            updateUIState(UIState.TRANSFERRING)
            
            lifecycleScope.launch {
                // 编码文件
                val sessionId = UUID.randomUUID().toString()
                val result = fileEncoder.encodeFile(file, qrConfig, sessionId)
                
                when (result) {
                    is FileEncoder.EncodingResult.Success -> {
                        fileChunks = result.chunks
                        currentSession = result.session
                        
                        // 显示编码信息
                        displayEncodingInfo(result)
                        
                        // 开始生成二维码
                        qrGeneratorManager.startGeneration(
                            chunks = fileChunks,
                            sessionId = sessionId
                        )
                        
                        // 更新传输信息
                        updateTransferInfo(result.session)
                    }
                    
                    is FileEncoder.EncodingResult.Error -> {
                        showError("编码失败: ${result.errorMessage}")
                        updateUIState(UIState.FILE_SELECTED)
                    }
                }
            }
        } ?: run {
            showError("请先选择文件")
        }
    }
    
    private fun displayEncodingInfo(result: FileEncoder.EncodingResult.Success) {
        binding.tvEncodingInfo.text = """
            编码信息:
            文件哈希: ${result.session.fileHash.take(16)}...
            总块数: ${result.session.totalChunks}
            压缩: ${if (result.session.compressionEnabled) "启用" else "禁用"}
            压缩率: ${String.format("%.2f", result.compressionInfo.compressionRatio)}
            纠错: ${if (result.session.errorCorrectionEnabled) "启用" else "禁用"}
            二维码版本: ${result.session.qrVersion}
        """.trimIndent()
    }
    
    private fun updateTransferInfo(session: TransferSession) {
        binding.tvTransferInfo.text = """
            传输信息:
            会话ID: ${session.id.take(8)}...
            恢复令牌: ${session.resumeToken.take(8)}...
            开始时间: ${Date(session.startTime)}
            预估时间: ${(session.getEstimatedRemainingTime() / 1000)}秒
        """.trimIndent()
    }
    
    private fun togglePauseResume() {
        if (qrGeneratorManager.getStats().completedChunks < fileChunks.size) {
            if (binding.btnPauseResume.text == "暂停") {
                qrGeneratorManager.pauseGeneration()
                binding.btnPauseResume.text = "继续"
                Toast.makeText(this, "传输已暂停", Toast.LENGTH_SHORT).show()
            } else {
                qrGeneratorManager.resumeGeneration()
                binding.btnPauseResume.text = "暂停"
                Toast.makeText(this, "传输已继续", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun stopTransfer() {
        qrGeneratorManager.stopGeneration()
        updateUIState(UIState.FILE_SELECTED)
        Toast.makeText(this, "传输已停止", Toast.LENGTH_SHORT).show()
    }
    
    private fun onTransferComplete() {
        updateUIState(UIState.COMPLETED)
        
        // 显示完成信息
        val stats = qrGeneratorManager.getStats()
        binding.tvCompletionInfo.text = """
            传输完成!
            总块数: ${stats.totalChunks}
            成功率: ${String.format("%.2f", stats.successRate * 100)}%
            平均扫描时间: ${stats.averageScanTime}ms
            总时间: ${stats.estimatedRemainingTime / 1000}秒
        """.trimIndent()
        
        Toast.makeText(this, "文件传输完成!", Toast.LENGTH_LONG).show()
    }
    
    private fun showConfigurationDialog() {
        // 创建配置对话框
        AlertDialog.Builder(this)
            .setTitle("传输配置")
            .setView(R.layout.dialog_config)
            .setPositiveButton("保存") { dialog, _ ->
                // TODO: 保存配置
                Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun updateChunkInfo(chunk: FileChunk, index: Int) {
        binding.tvCurrentChunk.text = "当前块: ${index + 1}/${chunk.totalChunks}"
        binding.tvChunkHash.text = "块哈希: ${chunk.dataHash.take(8)}..."
        
        // 更新统计信息
        val stats = qrGeneratorManager.getStats()
        binding.tvStats.text = """
            统计:
            成功率: ${String.format("%.2f", stats.successRate * 100)}%
            平均扫描时间: ${stats.averageScanTime}ms
            显示时间: ${stats.currentDisplayTime}ms
        """.trimIndent()
    }
    
    private fun updateProgress(current: Int, total: Int, displayTime: Long) {
        binding.progressBar.max = total
        binding.progressBar.progress = current
        
        binding.tvProgress.text = "$current/$total (${(current.toDouble() / total.toDouble() * 100).toInt()}%)"
        binding.tvDisplayTime.text = "显示时间: ${displayTime}ms"
        
        // 计算预估剩余时间
        val remaining = total - current
        val estimatedSeconds = (remaining * displayTime) / 1000
        binding.tvTimeRemaining.text = "剩余时间: ${estimatedSeconds}秒"
    }
    
    private fun updateUIState(state: UIState) {
        when (state) {
            UIState.SELECT_FILE -> {
                binding.btnSelectFile.isEnabled = true
                binding.btnStartTransfer.isEnabled = false
                binding.btnPauseResume.isEnabled = false
                binding.btnStop.isEnabled = false
                binding.btnConfig.isEnabled = true
                binding.btnPauseResume.text = "暂停"
                binding.llProgress.visibility = android.view.View.GONE
                binding.llCompletion.visibility = android.view.View.GONE
            }
            
            UIState.FILE_SELECTED -> {
                binding.btnSelectFile.isEnabled = true
                binding.btnStartTransfer.isEnabled = true
                binding.btnPauseResume.isEnabled = false
                binding.btnStop.isEnabled = false
                binding.btnConfig.isEnabled = true
                binding.llProgress.visibility = android.view.View.GONE
                binding.llCompletion.visibility = android.view.View.GONE
            }
            
            UIState.TRANSFERRING -> {
                binding.btnSelectFile.isEnabled = false
                binding.btnStartTransfer.isEnabled = false
                binding.btnPauseResume.isEnabled = true
                binding.btnStop.isEnabled = true
                binding.btnConfig.isEnabled = false
                binding.btnPauseResume.text = "暂停"
                binding.llProgress.visibility = android.view.View.VISIBLE
                binding.llCompletion.visibility = android.view.View.GONE
            }
            
            UIState.COMPLETED -> {
                binding.btnSelectFile.isEnabled = true
                binding.btnStartTransfer.isEnabled = false
                binding.btnPauseResume.isEnabled = false
                binding.btnStop.isEnabled = false
                binding.btnConfig.isEnabled = true
                binding.llProgress.visibility = android.view.View.GONE
                binding.llCompletion.visibility = android.view.View.VISIBLE
            }
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    enum class UIState {
        SELECT_FILE,
        FILE_SELECTED,
        TRANSFERRING,
        COMPLETED
    }
    
    // 添加Date导入
    import java.util.Date
}
