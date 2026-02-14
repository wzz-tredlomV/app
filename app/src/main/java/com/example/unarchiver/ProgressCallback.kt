package com.example.unarchiver

interface ProgressCallback {
    fun onFileStart(name: String, totalBytes: Long?)
    fun onFileProgress(name: String, readBytes: Long, totalBytes: Long?)
    fun onFileComplete(name: String)
    fun onOverallProgress(percent: Int)
}
