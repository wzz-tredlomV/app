package com.example.unarchiver

import org.junit.Assert.assertEquals
import org.junit.Test

class FileUtilTest {
    @Test
    fun sanitizeName_replacesIllegalChars() {
        val input = "weird / name:with*chars?.zip"
        val out = FileUtil.sanitizeName(input)
        assertEquals("weird___name_with_chars_.zip", out)
    }

    @Test
    fun sanitizeName_keepsGoodChars() {
        val input = "normal-file_name.7z"
        val out = FileUtil.sanitizeName(input)
        assertEquals(input, out)
    }
}
