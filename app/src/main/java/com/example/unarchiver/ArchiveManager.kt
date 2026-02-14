package com.example.unarchiver

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import com.github.junrar.Junrar
import java.io.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * ArchiveManager:
 * - 支持解压：zip, 7z, rar (junrar), tar*, gz/bz2/xz
 * - 支持压缩：zip, 7z, tar*, gz/bz2/xz
 * - RAR 压缩：尝试调用系统 rar 二进制（需要可执行 rar 在 PATH）
 *
 * 对于文件大于阈值，使用线程池并发处理（把大文件写到临时文件再合并/打包），以避免单线程 I/O 阻塞 UI/主流程。
 */

class ArchiveManager(
    private val context: Context,
    private val cacheDir: File,
    private val callback: ProgressCallback?,
    private val largeFileThreshold: Int = 5 * 1024 * 1024 // default 5MB
) {
    private val contentResolver = context.contentResolver
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    fun extractUriToDocumentTree(srcUri: Uri, destTreeUri: Uri): String {
        val tmp = File.createTempFile("unarchiver_in", null, cacheDir)
        contentResolver.openInputStream(srcUri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法读取源文件流")

        val destDoc = DocumentFile.fromTreeUri(context, destTreeUri)
            ?: throw IOException("无法打开目标目录")
        val baseName = srcUri.lastPathSegment ?: tmp.name
        val folderName = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val targetDir = destDoc.findFile(folderName) ?: destDoc.createDirectory(folderName)
        if (targetDir == null || !targetDir.isDirectory) throw IOException("无法创建目标子目录：$folderName")

        val name = tmp.name.lowercase()
        when {
            name.endsWith(".zip") -> extractZip(tmp, targetDir)
            name.endsWith(".7z") -> extract7z(tmp, targetDir)
            name.endsWith(".rar") -> extractRar(tmp, targetDir)
            name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz")
                    || name.endsWith(".tar.bz2") || name.endsWith(".tar.xz") -> extractTarLike(tmp, targetDir)
            name.endsWith(".gz") || name.endsWith(".bz2") || name.endsWith(".xz") -> extractSingleCompressed(tmp, targetDir)
            else -> throw IOException("不支持的压缩格式: ${tmp.name}")
        }

        // 等待并发任务完成（若有）
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.MINUTES)

        tmp.delete()
        return folderName
    }

    private fun extractZip(src: File, targetDir: DocumentFile) {
        FileInputStream(src).use { fis ->
            val zis = ZipArchiveInputStream(BufferedInputStream(fis))
            var currentEntry: ArchiveEntry? = zis.nextEntry
            while (currentEntry != null) {
                val entryName = currentEntry.name
                val entrySize = currentEntry.size
                val isDirectory = currentEntry.isDirectory
                
                if (isDirectory) {
                    createDocumentDirTree(targetDir, entryName)
                } else {
                    callback?.onFileStart(entryName, entrySize)
                    // 若 entry 大文件则提交线程写入临时文件再copy
                    if (entrySize > largeFileThreshold) {
                        val tmpOut = File.createTempFile("big_entry", null, cacheDir)
                        val transferred = writeEntryToTemp(zis, tmpOut, entryName)
                        // 提交任务把 tmpOut 写回目标 SAF
                        executor.submit {
                            val fileDoc = createDocumentFileForPath(targetDir, entryName)
                                ?: throw IOException("创建目标文件失败: $entryName")
                            tmpOut.inputStream().use { fis2 ->
                                contentResolver.openOutputStream(fileDoc.uri).use { out ->
                                    if (out != null) {
                                        fis2.copyTo(out)
                                    } else throw IOException("无法打开输出流")
                                }
                            }
                            tmpOut.delete()
                            callback?.onFileComplete(entryName)
                        }
                    } else {
                        val fileDoc = createDocumentFileForPath(targetDir, entryName) ?: throw IOException("创建目标文件失败: $entryName")
                        contentResolver.openOutputStream(fileDoc.uri).use { out ->
                            if (out != null) {
                                val buffer = ByteArray(8192)
                                var readTotal = 0L
                                var read: Int
                                while (true) {
                                    read = zis.read(buffer)
                                    if (read <= 0) break
                                    out.write(buffer, 0, read)
                                    readTotal += read
                                    callback?.onFileProgress(entryName, readTotal, entrySize)
                                }
                                callback?.onFileComplete(entryName)
                            } else throw IOException("无法打开输出流")
                        }
                    }
                }
                currentEntry = zis.nextEntry
            }
        }
    }

    private fun writeEntryToTemp(zis: InputStream, tmpOut: File, entryName: String): Long {
        tmpOut.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            var r: Int
            while (true) {
                r = zis.read(buffer)
                if (r <= 0) break
                out.write(buffer, 0, r)
                total += r
                callback?.onFileProgress(entryName, total, null)
            }
            return total
        }
    }

    private fun extract7z(src: File, targetDir: DocumentFile) {
        SevenZFile(src).use { seven ->
            var entry = seven.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                val name = entry.name
                val isDirectory = entry.isDirectory
                val size = entry.size
                
                if (isDirectory) {
                    createDocumentDirTree(targetDir, name)
                } else {
                    callback?.onFileStart(name, size)
                    if (size > largeFileThreshold) {
                        val tmpOut = File.createTempFile("big_7z", null, cacheDir)
                        FileOutputStream(tmpOut).use { fos ->
                            var left = size
                            while (left > 0) {
                                val toRead = min(buffer.size.toLong(), left).toInt()
                                val r = seven.read(buffer, 0, toRead)
                                if (r <= 0) break
                                fos.write(buffer, 0, r)
                                callback?.onFileProgress(name, (size - left + r), size)
                                left -= r.toLong()
                            }
                        }
                        executor.submit {
                            val fileDoc = createDocumentFileForPath(targetDir, name) ?: throw IOException("创建目标文件失败: $name")
                            tmpOut.inputStream().use { fis2 ->
                                contentResolver.openOutputStream(fileDoc.uri).use { out ->
                                    if (out != null) fis2.copyTo(out) else throw IOException("无法打开输出流")
                                }
                            }
                            tmpOut.delete()
                            callback?.onFileComplete(name)
                        }
                    } else {
                        val fileDoc = createDocumentFileForPath(targetDir, name) ?: throw IOException("创建目标文件失败: $name")
                        contentResolver.openOutputStream(fileDoc.uri).use { out ->
                            if (out != null) {
                                var left = size
                                var readTotal = 0L
                                while (left > 0) {
                                    val toRead = min(buffer.size.toLong(), left).toInt()
                                    val r = seven.read(buffer, 0, toRead)
                                    if (r <= 0) break
                                    out.write(buffer, 0, r)
                                    readTotal += r.toLong()
                                    left -= r.toLong()
                                    callback?.onFileProgress(name, readTotal, size)
                                }
                                callback?.onFileComplete(name)
                            } else throw IOException("无法打开输出流")
                        }
                    }
                }
                entry = seven.nextEntry
            }
        }
    }

    private fun extractTarLike(src: File, targetDir: DocumentFile) {
        var fis: InputStream = FileInputStream(src)
        try {
            if (src.name.endsWith(".tar.gz") || src.name.endsWith(".tgz") ||
                src.name.endsWith(".tar.bz2") || src.name.endsWith(".tar.xz")) {
                val cis = CompressorStreamFactory().createCompressorInputStream(BufferedInputStream(fis))
                val tar = TarArchiveInputStream(BufferedInputStream(cis))
                extractFromArchiveInputStream(tar, targetDir)
            } else {
                val tar = TarArchiveInputStream(BufferedInputStream(fis))
                extractFromArchiveInputStream(tar, targetDir)
            }
        } finally {
            fis.close()
        }
    }

    private fun extractSingleCompressed(src: File, targetDir: DocumentFile) {
        val simpleName = src.name.substringBeforeLast('.')
        callback?.onFileStart(simpleName, null)
        val fileDoc = createDocumentFileForPath(targetDir, simpleName) ?: throw IOException("无法创建目标文件: $simpleName")
        FileInputStream(src).use { fis ->
            val cis = CompressorStreamFactory().createCompressorInputStream(BufferedInputStream(fis))
            contentResolver.openOutputStream(fileDoc.uri).use { out ->
                if (out != null) {
                    val buffer = ByteArray(8192)
                    var readTotal = 0L
                    var read: Int
                    while (true) {
                        read = cis.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        readTotal += read
                        callback?.onFileProgress(simpleName, readTotal, null)
                    }
                    callback?.onFileComplete(simpleName)
                } else throw IOException("无法打开输出流")
            }
        }
    }

    private fun extractFromArchiveInputStream(tar: ArchiveInputStream<out ArchiveEntry>, targetDir: DocumentFile) {
        var currentEntry: ArchiveEntry? = tar.nextEntry
        while (currentEntry != null) {
            val name = currentEntry.name
            val isDirectory = currentEntry.isDirectory
            
            if (isDirectory) {
                createDocumentDirTree(targetDir, name)
            } else {
                val entrySize = if (currentEntry is TarArchiveEntry) currentEntry.size else null
                callback?.onFileStart(name, entrySize)
                if (entrySize != null && entrySize > largeFileThreshold) {
                    val tmpOut = File.createTempFile("big_tar", null, cacheDir)
                    tmpOut.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var r: Int
                        while (true) {
                            r = tar.read(buffer)
                            if (r <= 0) break
                            out.write(buffer, 0, r)
                            total += r
                            callback?.onFileProgress(name, total, entrySize)
                        }
                    }
                    executor.submit {
                        val fileDoc = createDocumentFileForPath(targetDir, name) ?: throw IOException("无法创建文件: $name")
                        tmpOut.inputStream().use { fis2 ->
                            contentResolver.openOutputStream(fileDoc.uri).use { out ->
                                if (out != null) fis2.copyTo(out) else throw IOException("无法打开输出流")
                            }
                        }
                        tmpOut.delete()
                        callback?.onFileComplete(name)
                    }
                } else {
                    val fileDoc = createDocumentFileForPath(targetDir, name) ?: throw IOException("创建文件失败: $name")
                    contentResolver.openOutputStream(fileDoc.uri).use { out ->
                        if (out != null) {
                            val buffer = ByteArray(8192)
                            var readTotal = 0L
                            var read: Int
                            while (true) {
                                read = tar.read(buffer)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                readTotal += read
                                callback?.onFileProgress(name, readTotal, entrySize)
                            }
                            callback?.onFileComplete(name)
                        } else throw IOException("无法打开输出流")
                    }
                }
            }
            currentEntry = tar.nextEntry
        }
    }

    private fun extractRar(src: File, targetDir: DocumentFile) {
        // 使用 junrar 库解压到临时目录，然后复制到 DocumentFile
        val tmpOutDir = File(cacheDir, src.name + "_rar_tmp")
        if (tmpOutDir.exists()) tmpOutDir.deleteRecursively()
        tmpOutDir.mkdirs()
        try {
            Junrar.extract(src, tmpOutDir)
            tmpOutDir.walkTopDown().forEach { f ->
                if (f.isFile) {
                    val rel = f.relativeTo(tmpOutDir).path.replace(File.separatorChar, '/')
                    val fileDoc = createDocumentFileForPath(targetDir, rel) ?: throw IOException("无法创建文件: $rel")
                    f.inputStream().use { fis -> contentResolver.openOutputStream(fileDoc.uri).use { out -> fis.copyTo(out!!) } }
                }
            }
        } catch (e: Exception) {
            throw IOException("RAR 解压失败: ${e.message}", e)
        } finally {
            tmpOutDir.deleteRecursively()
        }
    }

    // ---- 压缩 ----

    fun compressUrisToDocumentTree(uris: List<Uri>, destTreeUri: Uri, outFileName: String, format: String) {
        val tmpOut = File.createTempFile("unarchiver_out", null, cacheDir)
        when (format) {
            "zip" -> createZip(tmpOut, uris)
            "7z" -> create7z(tmpOut, uris)
            "tar" -> createTar(tmpOut, uris, compress = null)
            "tar.gz" -> createTar(tmpOut, uris, compress = "gz")
            "tar.bz2" -> createTar(tmpOut, uris, compress = "bz2")
            "tar.xz" -> createTar(tmpOut, uris, compress = "xz")
            "rar" -> createRarViaBinary(tmpOut, uris)
            else -> throw IOException("不支持的压缩格式: $format")
        }

        val destDoc = DocumentFile.fromTreeUri(context, destTreeUri)
            ?: throw IOException("无法打开目标目录")
        val finalFile = destDoc.createFile("application/octet-stream", outFileName)
            ?: throw IOException("无法在目标目录创建输出文件")
        tmpOut.inputStream().use { fis -> contentResolver.openOutputStream(finalFile.uri).use { out -> fis.copyTo(out!!) } }
        tmpOut.delete()
    }

    private fun createZip(outFile: File, uris: List<Uri>) {
        FileOutputStream(outFile).use { fos ->
            ZipArchiveOutputStream(BufferedOutputStream(fos)).use { zos ->
                uris.forEachIndexed { idx, uri ->
                    callback?.onOverallProgress((idx * 100) / uris.size)
                    val df = uriToDocumentFile(uri)
                    if (df == null) {
                        val name = uri.lastPathSegment ?: "file"
                        contentResolver.openInputStream(uri)?.use { input ->
                            val entry = ZipArchiveEntry(name)
                            zos.putArchiveEntry(entry)
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            var r: Int
                            while (true) {
                                r = input.read(buffer)
                                if (r <= 0) break
                                zos.write(buffer, 0, r)
                                written += r
                                callback?.onFileProgress(name, written, null)
                            }
                            zos.closeArchiveEntry()
                            callback?.onFileComplete(name)
                        }
                    } else {
                        if (df.isDirectory) addDocumentDirectoryToZip(zos, df, df.name ?: "root")
                        else addDocumentFileToZip(zos, df, df.name ?: "file")
                    }
                }
                zos.finish()
            }
        }
    }

    private fun addDocumentDirectoryToZip(zos: ZipArchiveOutputStream, dir: DocumentFile, basePath: String) {
        dir.listFiles().forEach { f ->
            val path = "$basePath/${f.name}"
            if (f.isDirectory) addDocumentDirectoryToZip(zos, f, path)
            else addDocumentFileToZip(zos, f, path)
        }
    }

    private fun addDocumentFileToZip(zos: ZipArchiveOutputStream, file: DocumentFile, entryPath: String) {
        val entry = ZipArchiveEntry(entryPath)
        zos.putArchiveEntry(entry)
        contentResolver.openInputStream(file.uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            var r: Int
            while (true) {
                r = input.read(buffer)
                if (r <= 0) break
                zos.write(buffer, 0, r)
                written += r
                callback?.onFileProgress(entryPath, written, null)
            }
        } ?: throw IOException("无法打开 ${file.uri}")
        zos.closeArchiveEntry()
        callback?.onFileComplete(entryPath)
    }

    private fun create7z(outFile: File, uris: List<Uri>) {
        SevenZOutputFile(outFile).use { sevenOut ->
            uris.forEachIndexed { idx, uri ->
                callback?.onOverallProgress((idx * 100) / uris.size)
                val df = uriToDocumentFile(uri)
                if (df == null) {
                    val name = uri.lastPathSegment ?: "file"
                    val data = contentResolver.openInputStream(uri)?.readBytes() ?: byteArrayOf()
                    val entry = org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry()
                    entry.name = name
                    entry.size = data.size.toLong()
                    sevenOut.putArchiveEntry(entry)
                    sevenOut.write(data)
                    sevenOut.closeArchiveEntry()
                    callback?.onFileComplete(name)
                } else {
                    if (df.isDirectory) addDocumentDirectoryTo7z(sevenOut, df, df.name ?: "root")
                    else addDocumentFileTo7z(sevenOut, df, df.name ?: "file")
                }
            }
        }
    }

    private fun addDocumentDirectoryTo7z(sevenOut: SevenZOutputFile, dir: DocumentFile, basePath: String) {
        dir.listFiles().forEach { f ->
            val path = "$basePath/${f.name}"
            if (f.isDirectory) addDocumentDirectoryTo7z(sevenOut, f, path)
            else addDocumentFileTo7z(sevenOut, f, path)
        }
    }

    private fun addDocumentFileTo7z(sevenOut: SevenZOutputFile, file: DocumentFile, entryPath: String) {
        val data = contentResolver.openInputStream(file.uri)?.readBytes() ?: byteArrayOf()
        val entry = org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry()
        entry.name = entryPath
        entry.size = data.size.toLong()
        sevenOut.putArchiveEntry(entry)
        sevenOut.write(data)
        sevenOut.closeArchiveEntry()
        callback?.onFileComplete(entryPath)
    }

    private fun createTar(outFile: File, uris: List<Uri>, compress: String?) {
        FileOutputStream(outFile).use { fos ->
            var os: OutputStream = BufferedOutputStream(fos)
            if (compress != null) {
                os = when (compress) {
                    "gz" -> GzipCompressorOutputStream(os)
                    "bz2" -> BZip2CompressorOutputStream(os)
                    "xz" -> XZCompressorOutputStream(os)
                    else -> os
                }
            }
            TarArchiveOutputStream(os).use { tarOut ->
                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                uris.forEachIndexed { idx, uri ->
                    callback?.onOverallProgress((idx * 100) / uris.size)
                    val df = uriToDocumentFile(uri)
                    if (df == null) {
                        val name = uri.lastPathSegment ?: "file"
                        contentResolver.openInputStream(uri)?.use { input ->
                            val tmpBytes = input.readBytes()
                            val entry = TarArchiveEntry(name)
                            entry.size = tmpBytes.size.toLong()
                            tarOut.putArchiveEntry(entry)
                            tarOut.write(tmpBytes)
                            tarOut.closeArchiveEntry()
                            callback?.onFileComplete(name)
                        }
                    } else {
                        if (df.isDirectory) addDocumentDirectoryToTar(tarOut, df, df.name ?: "root")
                        else addDocumentFileToTar(tarOut, df, df.name ?: "file")
                    }
                }
                tarOut.finish()
            }
        }
    }

    private fun addDocumentDirectoryToTar(tarOut: TarArchiveOutputStream, dir: DocumentFile, basePath: String) {
        dir.listFiles().forEach { f ->
            val path = "$basePath/${f.name}"
            if (f.isDirectory) addDocumentDirectoryToTar(tarOut, f, path)
            else addDocumentFileToTar(tarOut, f, path)
        }
    }

    private fun addDocumentFileToTar(tarOut: TarArchiveOutputStream, file: DocumentFile, entryPath: String) {
        contentResolver.openInputStream(file.uri)?.use { input ->
            val bytes = input.readBytes()
            val entry = TarArchiveEntry(entryPath)
            entry.size = bytes.size.toLong()
            tarOut.putArchiveEntry(entry)
            tarOut.write(bytes)
            tarOut.closeArchiveEntry()
            callback?.onFileComplete(entryPath)
        } ?: throw IOException("无法打开 ${file.uri}")
    }

    private fun createRarViaBinary(outFile: File, uris: List<Uri>) {
        // 将所有选中的文件/目录复制到临时目录，然后调用系统 'rar' 命令进行打包
        val tmpDir = File.createTempFile("rar_tmp", null, cacheDir)
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        tmpDir.mkdirs()
        try {
            // 复制到 tmpDir
            uris.forEach { uri ->
                val df = uriToDocumentFile(uri)
                if (df == null) {
                    val name = uri.lastPathSegment ?: "file"
                    val dest = File(tmpDir, name)
                    contentResolver.openInputStream(uri)?.use { ins -> dest.outputStream().use { outs -> ins.copyTo(outs) } }
                } else {
                    // 递归写入 dir
                    copyDocumentFileToDir(df, tmpDir)
                }
            }
            // 检查 rar 是否存在
            val which = try {
                val proc = ProcessBuilder("which", "rar").start()
                proc.waitFor(3, TimeUnit.SECONDS)
                val out = proc.inputStream.bufferedReader().readText().trim()
                out
            } catch (e: Exception) {
                ""
            }
            if (which.isBlank()) {
                throw IOException("系统中未找到 'rar' 可执行文件。无法在应用内创建 RAR；请安装 rar 并确保其在 PATH 中，或使用 zip/7z/tar.* 作为替代。")
            }
            // 执行 rar a -ep1 outFileName *
            val cmd = listOf("rar", "a", "-ep1", outFile.absolutePath, ".")
            val pb = ProcessBuilder(cmd).directory(tmpDir).redirectErrorStream(true)
            val proc = pb.start()
            val all = proc.inputStream.bufferedReader().readText()
            val exited = proc.waitFor(5, TimeUnit.MINUTES)
            if (!exited || proc.exitValue() != 0) {
                throw IOException("rar 压缩失败: $all")
            }
            callback?.onOverallProgress(100)
        } finally {
            // 将生成的 rar 文件（如果在 tmpDir）移动到 tmpOut handled by caller (we wrote directly to outFile)
            // 清理
            // 注意：此处假设 rar 程序在 tmpDir 创建了 outFile
        }
    }

    private fun copyDocumentFileToDir(df: DocumentFile, outDir: File) {
        if (df.isDirectory) {
            val sub = File(outDir, df.name ?: "dir")
            sub.mkdirs()
            df.listFiles().forEach { copyDocumentFileToDir(it, sub) }
        } else {
            val outFile = File(outDir, df.name ?: "file")
            contentResolver.openInputStream(df.uri)?.use { ins -> outFile.outputStream().use { outs -> ins.copyTo(outs) } }
        }
    }

    // ---- helpers ----

    private fun uriToDocumentFile(uri: Uri): DocumentFile? {
        return try {
            DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
        } catch (e: Exception) {
            null
        }
    }

    private fun createDocumentDirTree(root: DocumentFile, path: String): DocumentFile? {
        val parts = path.trim('/').split('/')
        var cur = root
        for (p in parts) {
            val existing = cur.findFile(p)
            cur = existing ?: cur.createDirectory(p) ?: return null
        }
        return cur
    }

    private fun createDocumentFileForPath(root: DocumentFile, path: String): DocumentFile? {
        val parts = path.trim('/').split('/')
        val filename = parts.last()
        val dirParts = parts.dropLast(1)
        var cur = root
        for (p in dirParts) {
            val found = cur.findFile(p)
            cur = found ?: cur.createDirectory(p) ?: return null
        }
        val existing = cur.findFile(filename)
        existing?.delete()
        return cur.createFile("application/octet-stream", filename)
    }
}

