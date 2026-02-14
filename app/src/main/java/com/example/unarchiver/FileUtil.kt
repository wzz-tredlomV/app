package com.example.unarchiver

object FileUtil {
    fun sanitizeName(input: String): String {
        return input.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
