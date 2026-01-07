package com.qrfiletransfer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import com.qrfiletransfer.receiver.ResumeDatabase

class QRFileTransferApp : Application() {
    
    companion object {
        private const val TAG = "QRFileTransferApp"
        
        @Volatile
        private var instance: QRFileTransferApp? = null
        
        fun getAppContext(): Context {
            return instance!!.applicationContext
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化数据库
        initDatabase()
        
        // 初始化 WorkManager
        initWorkManager()
        
        // 初始化其他组件
        initComponents()
        
        Log.d(TAG, "Application created")
    }
    
    private fun initDatabase() {
        // 数据库初始化将在第一次访问时延迟进行
        ResumeDatabase.getDatabase(this)
    }
    
    private fun initWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        
        WorkManager.initialize(this, config)
    }
    
    private fun initComponents() {
        // 这里可以初始化其他全局组件
        // 例如：崩溃报告、分析、依赖注入等
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application terminating")
    }
}
