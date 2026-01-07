package com.qrfiletransfer.model

import java.io.File
import java.util.Date

data class FileInfo(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String? = null,
    val extension: String = ""
) {
    constructor(file: File) : this(
        path = file.absolutePath,
        name = file.name,
        size = file.length(),
        lastModified = file.lastModified(),
        extension = file.extension.lowercase()
    )
    
    fun getFormattedSize(): String {
        return formatFileSize(size)
    }
    
    fun getFormattedLastModified(): String {
        return Date(lastModified).toString()
    }
    
    fun getFileType(): FileType {
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> FileType.IMAGE
            "mp4", "avi", "mkv", "mov", "wmv" -> FileType.VIDEO
            "mp3", "wav", "flac", "aac", "ogg" -> FileType.AUDIO
            "pdf" -> FileType.PDF
            "doc", "docx" -> FileType.WORD
            "xls", "xlsx" -> FileType.EXCEL
            "ppt", "pptx" -> FileType.POWERPOINT
            "txt", "md", "json", "xml" -> FileType.TEXT
            "zip", "rar", "7z", "tar", "gz" -> FileType.ARCHIVE
            "apk" -> FileType.APK
            else -> FileType.OTHER
        }
    }
    
    fun getIconResource(): Int {
        return when (getFileType()) {
            FileType.IMAGE -> R.drawable.ic_file_image
            FileType.VIDEO -> R.drawable.ic_file_video
            FileType.AUDIO -> R.drawable.ic_file_audio
            FileType.PDF -> R.drawable.ic_file_pdf
            FileType.WORD -> R.drawable.ic_file_word
            FileType.EXCEL -> R.drawable.ic_file_excel
            FileType.POWERPOINT -> R.drawable.ic_file_powerpoint
            FileType.TEXT -> R.drawable.ic_file_text
            FileType.ARCHIVE -> R.drawable.ic_file_archive
            FileType.APK -> R.drawable.ic_file_apk
            FileType.OTHER -> R.drawable.ic_file_unknown
        }
    }
    
    fun getColorResource(): Int {
        return when (getFileType()) {
            FileType.IMAGE -> R.color.file_type_image
            FileType.VIDEO -> R.color.file_type_video
            FileType.AUDIO -> R.color.file_type_audio
            FileType.PDF -> R.color.file_type_document
            FileType.WORD -> R.color.file_type_document
            FileType.EXCEL -> R.color.file_type_document
            FileType.POWERPOINT -> R.color.file_type_document
            FileType.TEXT -> R.color.file_type_document
            FileType.ARCHIVE -> R.color.file_type_archive
            FileType.APK -> R.color.file_type_other
            FileType.OTHER -> R.color.file_type_other
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

enum class FileType {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    WORD,
    EXCEL,
    POWERPOINT,
    TEXT,
    ARCHIVE,
    APK,
    OTHER
}
