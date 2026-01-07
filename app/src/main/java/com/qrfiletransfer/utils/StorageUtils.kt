package com.qrfiletransfer.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageUtils {
    
    // 获取应用专用存储目录
    fun getAppStorageDir(context: Context, subDir: String = "QRFileTransfer"): File {
        return File(context.getExternalFilesDir(null), subDir).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    // 获取公共下载目录
    fun getPublicDownloadDir(subDir: String = "QRFileTransfer"): File? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return if (downloadsDir != null) {
            File(downloadsDir, subDir).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
        } else {
            null
        }
    }
    
    // 生成唯一的文件名
    fun generateUniqueFileName(
        originalName: String,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timeString = dateFormat.format(Date(timestamp))
        
        val extension = originalName.substringAfterLast(".", "")
        val baseName = originalName.substringBeforeLast(".")
        
        return if (extension.isNotEmpty()) {
            "${baseName}_${timeString}.$extension"
        } else {
            "${baseName}_$timeString"
        }
    }
    
    // 获取文件大小，带单位
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
    
    // 计算可用存储空间
    fun getAvailableStorageSpace(path: String): Long {
        return try {
            File(path).freeSpace
        } catch (e: SecurityException) {
            -1L
        }
    }
    
    // 检查存储空间是否足够
    fun hasEnoughSpace(path: String, requiredSize: Long): Boolean {
        return getAvailableStorageSpace(path) >= requiredSize * 1.1
    }
    
    // 清理临时文件
    fun cleanupTempFiles(dir: File, maxAgeMillis: Long = 24 * 60 * 60 * 1000) {
        val cutoffTime = System.currentTimeMillis() - maxAgeMillis
        
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.deleteRecursively()
            }
        }
    }
    
    // 获取文件MIME类型
    fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            else -> "application/octet-stream"
        }
    }
    
    // 安全删除文件
    fun secureDelete(file: File, passes: Int = 3) {
        if (file.exists()) {
            val length = file.length()
            
            for (pass in 0 until passes) {
                try {
                    file.writeBytes(ByteArray(length.toInt()) { 
                        (Math.random() * 256).toByte() 
                    })
                } catch (e: Exception) {
                    continue
                }
            }
            
            file.delete()
        }
    }
    
    // 计算目录大小
    fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        
        return size
    }
    
    // 检查文件是否可读
    fun isFileReadable(file: File): Boolean {
        return file.exists() && file.canRead() && file.length() > 0
    }
    
    // 检查文件是否可写
    fun isFileWritable(file: File): Boolean {
        return if (file.exists()) {
            file.canWrite()
        } else {
            try {
                file.createNewFile()
                file.delete()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    // 获取文件扩展名
    fun getFileExtension(file: File): String {
        return file.name.substringAfterLast(".", "").lowercase()
    }
    
    // 创建临时文件
    fun createTempFile(context: Context, prefix: String = "tmp", suffix: String = ".tmp"): File {
        return File.createTempFile(prefix, suffix, context.cacheDir)
    }
    
    // 复制文件
    fun copyFile(source: File, destination: File): Boolean {
        return try {
            source.copyTo(destination, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // 移动文件
    fun moveFile(source: File, destination: File): Boolean {
        return try {
            source.copyTo(destination, overwrite = true)
            source.delete()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // 重命名文件
    fun renameFile(file: File, newName: String): Boolean {
        return try {
            val newFile = File(file.parent, newName)
            file.renameTo(newFile)
        } catch (e: Exception) {
            false
        }
    }
    
    // 获取目录中的所有文件
    fun listFilesInDirectory(directory: File): List<File> {
        return directory.listFiles()?.toList() ?: emptyList()
    }
    
    // 获取目录中的所有文件（按修改时间排序）
    fun listFilesSortedByDate(directory: File, ascending: Boolean = false): List<File> {
        return listFilesInDirectory(directory).sortedBy {
            if (ascending) it.lastModified() else -it.lastModified()
        }
    }
    
    // 获取目录中的所有文件（按大小排序）
    fun listFilesSortedBySize(directory: File, ascending: Boolean = false): List<File> {
        return listFilesInDirectory(directory).sortedBy {
            if (ascending) it.length() else -it.length()
        }
    }
    
    // 获取目录中的所有文件（按名称排序）
    fun listFilesSortedByName(directory: File, ascending: Boolean = true): List<File> {
        return listFilesInDirectory(directory).sortedBy {
            if (ascending) it.name else -it.name.hashCode()
        }
    }
    
    // 过滤文件类型
    fun filterFilesByExtension(files: List<File>, extensions: List<String>): List<File> {
        return files.filter { file ->
            extensions.any { ext -> file.name.endsWith(ext, ignoreCase = true) }
        }
    }
    
    // 获取文件创建时间
    fun getFileCreationTime(file: File): Long? {
        return try {
            val attr = java.nio.file.Files.readAttributes(
                file.toPath(),
                java.nio.file.attribute.BasicFileAttributes::class.java
            )
            attr.creationTime().toMillis()
        } catch (e: Exception) {
            null
        }
    }
    
    // 获取文件最后访问时间
    fun getFileLastAccessTime(file: File): Long? {
        return try {
            val attr = java.nio.file.Files.readAttributes(
                file.toPath(),
                java.nio.file.attribute.BasicFileAttributes::class.java
            )
            attr.lastAccessTime().toMillis()
        } catch (e: Exception) {
            null
        }
    }
    
    // 检查是否是隐藏文件
    fun isHiddenFile(file: File): Boolean {
        return file.isHidden || file.name.startsWith(".")
    }
    
    // 获取文件权限
    fun getFilePermissions(file: File): String {
        return try {
            val perms = java.nio.file.Files.getPosixFilePermissions(file.toPath())
            perms.toString()
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    // 设置文件权限
    fun setFilePermissions(file: File, permissions: String): Boolean {
        return try {
            val perms = java.nio.file.attribute.PosixFilePermissions.fromString(permissions)
            java.nio.file.Files.setPosixFilePermissions(file.toPath(), perms)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // 计算文件哈希（MD5）
    fun calculateFileMD5(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            BinaryUtils.bytesToHex(digest.digest())
        } catch (e: Exception) {
            null
        }
    }
    
    // 计算文件哈希（SHA-1）
    fun calculateFileSHA1(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            BinaryUtils.bytesToHex(digest.digest())
        } catch (e: Exception) {
            null
        }
    }
    
    // 比较两个文件是否相同
    fun compareFiles(file1: File, file2: File): Boolean {
        if (file1.length() != file2.length()) return false
        
        return file1.inputStream().use { input1 ->
            file2.inputStream().use { input2 ->
                val buffer1 = ByteArray(8192)
                val buffer2 = ByteArray(8192)
                
                while (true) {
                    val bytesRead1 = input1.read(buffer1)
                    val bytesRead2 = input2.read(buffer2)
                    
                    if (bytesRead1 != bytesRead2) return false
                    if (bytesRead1 == -1) return true
                    if (!buffer1.copyOf(bytesRead1).contentEquals(buffer2.copyOf(bytesRead2))) return false
                }
                
                true
            }
        }
    }
}
