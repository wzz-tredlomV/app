package com.qrfiletransfer.receiver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.qrfiletransfer.model.QrData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 二维码扫描管理器
 */
class QRScannerManager(
    private val context: Context,
    private val lifecycle: Lifecycle
) : LifecycleObserver {
    
    companion object {
        private const val TAG = "QRScannerManager"
    }
    
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner
    private var imageAnalysis: ImageAnalysis? = null
    
    private var isScanning = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var onQRCodeScanned: ((String) -> Unit)? = null
    private var onError: ((Exception) -> Unit)? = null
    
    init {
        lifecycle.addObserver(this)
        initScanner()
    }
    
    private fun initScanner() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAllPotentialBarcodes()
            .build()
        
        barcodeScanner = BarcodeScanning.getClient(options)
    }
    
    fun setCallbacks(
        onQRCodeScanned: ((String) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        this.onQRCodeScanned = onQRCodeScanned
        this.onError = onError
    }
    
    fun startScanning() {
        if (!isScanning) {
            isScanning = true
            startCamera()
        }
    }
    
    fun stopScanning() {
        if (isScanning) {
            isScanning = false
            stopCamera()
        }
    }
    
    fun pauseScanning() {
        imageAnalysis?.clearAnalyzer()
    }
    
    fun resumeScanning() {
        if (isScanning) {
            startCamera()
        }
    }
    
    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            onError?.invoke(SecurityException("相机权限未授予"))
            return
        }
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
        
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        
        val preview = Preview.Builder().build()
        
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        
        imageAnalysis?.setAnalyzer(
            cameraExecutor,
            createQrCodeAnalyzer()
        )
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycle as androidx.lifecycle.LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            onError?.invoke(e)
        }
    }
    
    private fun createQrCodeAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            try {
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )
                    
                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstOrNull()?.let { barcode ->
                                val qrData = barcode.rawValue
                                if (qrData != null) {
                                    mainHandler.post {
                                        onQRCodeScanned?.invoke(qrData)
                                    }
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            onError?.invoke(e)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            } catch (e: Exception) {
                imageProxy.close()
                onError?.invoke(e)
            }
        }
    }
    
    private fun stopCamera() {
        imageAnalysis?.clearAnalyzer()
        cameraExecutor.shutdown()
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        pauseScanning()
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        if (isScanning) {
            resumeScanning()
        }
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        stopScanning()
        lifecycle.removeObserver(this)
    }
}
