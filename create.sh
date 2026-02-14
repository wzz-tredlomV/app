#!/usr/bin/env bash
set -euo pipefail

ROOT="unarchiver-app"
ZIP_NAME="${ROOT}.zip"
LARGE_FILE_THRESHOLD=$((5 * 1024 * 1024)) # 5 MB, 超过这个大小时启用线程池并行处理

echo "创建 Android 项目骨架到 ./$ROOT ..."
echo "大文件阈值: ${LARGE_FILE_THRESHOLD} bytes"

# 清理旧目录
rm -rf "$ROOT" "$ZIP_NAME"

# 目录
mkdir -p "$ROOT/app/src/main/java/com/example/unarchiver"
mkdir -p "$ROOT/app/src/main/res/layout"
mkdir -p "$ROOT/app/src/main/res/values"
mkdir -p "$ROOT/app/src/main/res/mipmap-mdpi"
mkdir -p "$ROOT/app/src/main/res/mipmap-hdpi"
mkdir -p "$ROOT/app/src/main/res/mipmap-xhdpi"
mkdir -p "$ROOT/app/src/main/res/mipmap-xxhdpi"
mkdir -p "$ROOT/app/src/main/res/mipmap-xxxhdpi"
mkdir -p "$ROOT/app/src/main/assets"

# 测试目录
mkdir -p "$ROOT/app/src/test/java/com/example/unarchiver"
mkdir -p "$ROOT/app/src/androidTest/java/com/example/unarchiver"

# settings.gradle
cat > "$ROOT/settings.gradle" <<'EOF'
rootProject.name = "Unarchiver"
include(":app")
EOF

# gradle.properties
cat > "$ROOT/gradle.properties" <<'EOF'
org.gradle.jvmargs=-Xmx1536m
android.useAndroidX=true
kotlin.code.style=official
EOF

# project build.gradle
cat > "$ROOT/build.gradle" <<'EOF'
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath "com.android.tools.build:gradle:8.1.0"
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0"
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
EOF

# app build.gradle (包含 junrar 以支持 RAR 解压)
cat > "$ROOT/app/build.gradle" <<'EOF'
plugins {
    id 'com.android.application'
    id 'kotlin-android'
}

android {
    namespace 'com.example.unarchiver'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.unarchiver"
        minSdk 26            // Android 8.0
        targetSdk 31         // 保持对 Android 12 的兼容
        versionCode 1
        versionName "0.4"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests {
            includeAndroidResources = true
        }
    }
}

dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.9.0"
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.9.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.2.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'androidx.documentfile:documentfile:1.0.1'
    implementation 'org.apache.commons:commons-compress:1.23.0'
    implementation 'androidx.core:core:1.10.1'

    // junrar: 用于 RAR 解压（只支持解压）
    implementation 'com.github.junrar:junrar:7.5.4'

    // 测试依赖
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.robolectric:robolectric:4.11'
    testImplementation 'androidx.test:core:1.5.0'
    testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'

    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
    androidTestImplementation 'androidx.test:core-ktx:1.5.0'
}
EOF

# AndroidManifest.xml
cat > "$ROOT/app/src/main/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest package="com.example.unarchiver"
    xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" android:required="false" />

    <application
        android:label="@string/app_name"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:theme="@style/Theme.Unarchiver">
        <activity android:name="com.example.unarchiver.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

# layout
cat > "$ROOT/app/src/main/res/layout/activity_main.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="com.example.unarchiver.MainActivity">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/topAppBar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:backgroundTint="?attr/colorPrimary"
        android:title="@string/app_name" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_marginTop="?attr/actionBarSize"
        android:padding="16dp">

        <LinearLayout
            android:orientation="vertical"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <TextView
                android:id="@+id/tv_selected_files"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/no_file_selected" />

            <Button
                android:id="@+id/btn_pick_files"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/select_files" />

            <TextView
                android:id="@+id/tv_selected_dest"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="@string/no_destination_selected" />

            <Button
                android:id="@+id/btn_pick_dest"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/select_destination" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="12dp">

                <Spinner
                    android:id="@+id/spinner_format"
                    android:layout_width="0dp"
                    android:layout_weight="1"
                    android:layout_height="wrap_content" />

                <EditText
                    android:id="@+id/et_output_name"
                    android:layout_width="0dp"
                    android:layout_weight="1"
                    android:layout_height="wrap_content"
                    android:hint="@string/output_filename" />
            </LinearLayout>

            <Button
                android:id="@+id/btn_compress"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/compress"
                android:enabled="false"
                android:layout_marginTop="12dp"/>

            <Button
                android:id="@+id/btn_extract"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/extract"
                android:enabled="false"
                android:layout_marginTop="8dp" />

            <ProgressBar
                android:id="@+id/progress"
                style="?android:attr/progressBarStyleHorizontal"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:visibility="gone"/>

            <TextView
                android:id="@+id/tv_status"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="" />

            <TextView
                android:id="@+id/tv_current_file"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:text="" />

        </LinearLayout>

    </ScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
EOF

# strings + colors + themes
cat > "$ROOT/app/src/main/res/values/strings.xml" <<'EOF'
<resources>
    <string name="app_name">Unarchiver</string>
    <string name="no_file_selected">未选择文件/文件夹</string>
    <string name="select_files">选择文件/文件夹</string>
    <string name="no_destination_selected">未选择输出目录</string>
    <string name="select_destination">选择输出目录</string>
    <string name="output_filename">输出文件名（不带后缀）</string>
    <string name="compress">压缩</string>
    <string name="extract">解压</string>
</resources>
EOF

cat > "$ROOT/app/src/main/res/values/colors.xml" <<'EOF'
<resources>
    <color name="purple_500">#6200EE</color>
    <color name="purple_700">#3700B3</color>
    <color name="white">#FFFFFF</color>
</resources>
EOF

cat > "$ROOT/app/src/main/res/values/themes.xml" <<'EOF'
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.Unarchiver" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">@color/purple_500</item>
        <item name="colorPrimaryVariant">@color/purple_700</item>
        <item name="colorOnPrimary">@color/white</item>
    </style>
</resources>
EOF

# MainActivity.kt (订阅 ProgressCallback、显示逐文件进度并通过 NotificationHelper 更新通知)
cat > "$ROOT/app/src/main/java/com/example/unarchiver/MainActivity.kt" <<'EOF'
package com.example.unarchiver

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var tvSelectedFiles: TextView
    private lateinit var tvSelectedDest: TextView
    private lateinit var btnPickFiles: Button
    private lateinit var btnPickDest: Button
    private lateinit var btnCompress: Button
    private lateinit var btnExtract: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvCurrentFile: TextView
    private lateinit var spinnerFormat: Spinner
    private lateinit var etOutputName: EditText

    private var selectedUris: List<Uri> = emptyList()
    private var selectedDestTreeUri: Uri? = null

    private val pickFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            selectedUris = uris
            tvSelectedFiles.text = uris.joinToString("\n") { it.lastPathSegment ?: it.toString() }
            uris.forEach { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        } else {
            selectedUris = emptyList()
            tvSelectedFiles.text = getString(R.string.no_file_selected)
        }
        updateButtons()
    }

    private val pickDestLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            selectedDestTreeUri = uri
            tvSelectedDest.text = uri.path ?: uri.toString()
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } else {
            selectedDestTreeUri = null
            tvSelectedDest.text = getString(R.string.no_destination_selected)
        }
        updateButtons()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSelectedFiles = findViewById(R.id.tv_selected_files)
        tvSelectedDest = findViewById(R.id.tv_selected_dest)
        btnPickFiles = findViewById(R.id.btn_pick_files)
        btnPickDest = findViewById(R.id.btn_pick_dest)
        btnCompress = findViewById(R.id.btn_compress)
        btnExtract = findViewById(R.id.btn_extract)
        progressBar = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tv_status)
        tvCurrentFile = findViewById(R.id.tv_current_file)
        spinnerFormat = findViewById(R.id.spinner_format)
        etOutputName = findViewById(R.id.et_output_name)

        btnPickFiles.setOnClickListener { pickFilesLauncher.launch(arrayOf("*/*")) }
        btnPickDest.setOnClickListener { pickDestLauncher.launch(null) }

        val formats = listOf("zip", "7z", "tar", "tar.gz", "tar.bz2", "tar.xz", "rar")
        spinnerFormat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formats)

        btnCompress.setOnClickListener { startCompress() }
        btnExtract.setOnClickListener { startExtract() }

        updateButtons()
    }

    private fun updateButtons() {
        val hasSelection = selectedUris.isNotEmpty()
        val hasDest = selectedDestTreeUri != null
        btnCompress.isEnabled = hasSelection && hasDest && etOutputName.text.isNotBlank()
        btnExtract.isEnabled = selectedUris.size == 1 && hasDest
    }

    private fun startCompress() {
        val destTree = selectedDestTreeUri ?: return
        val format = spinnerFormat.selectedItem as String
        val outputName = etOutputName.text.toString().trim()
        if (outputName.isEmpty()) {
            toast("请填写输出文件名")
            return
        }

        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        tvStatus.text = "开始压缩..."
        tvCurrentFile.text = ""
        setUiEnabled(false)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val notificationHelper = NotificationHelper(this@MainActivity)
                    val manager = ArchiveManager(this@MainActivity, cacheDir, object : ProgressCallback {
                        override fun onFileStart(name: String, totalBytes: Long?) {
                            notificationHelper.notifyFileStart(name, totalBytes)
                            runOnUiThread { tvCurrentFile.text = name }
                        }
                        override fun onFileProgress(name: String, readBytes: Long, totalBytes: Long?) {
                            val percent = if (totalBytes != null && totalBytes > 0) ((readBytes * 100 / totalBytes).toInt()) else 0
                            notificationHelper.notifyFileProgress(name, percent, readBytes, totalBytes)
                            runOnUiThread { progressBar.progress = percent }
                        }
                        override fun onFileComplete(name: String) {
                            notificationHelper.notifyFileComplete(name)
                        }
                        override fun onOverallProgress(percent: Int) {
                            notificationHelper.notifyOverallProgress(percent)
                            runOnUiThread { progressBar.progress = percent }
                        }
                    })
                    val fileName = when {
                        format == "tar.gz" -> "$outputName.tar.gz"
                        format == "tar.bz2" -> "$outputName.tar.bz2"
                        format == "tar.xz" -> "$outputName.tar.xz"
                        format == "tar" -> "$outputName.tar"
                        else -> "$outputName.$format"
                    }
                    manager.compressUrisToDocumentTree(selectedUris, destTree, fileName, format)
                    "压缩完成: $fileName"
                } catch (e: Exception) {
                    "压缩失败: ${e.message}"
                }
            }
            progressBar.visibility = View.GONE
            tvStatus.text = result
            tvCurrentFile.text = ""
            setUiEnabled(true)
        }
    }

    private fun startExtract() {
        if (selectedUris.size != 1) {
            toast("请仅选择一个压缩包以解压")
            return
        }
        val srcUri = selectedUris[0]
        val destTree = selectedDestTreeUri ?: return

        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        tvStatus.text = "开始解压..."
        tvCurrentFile.text = ""
        setUiEnabled(false)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val notificationHelper = NotificationHelper(this@MainActivity)
                    val manager = ArchiveManager(this@MainActivity, cacheDir, object : ProgressCallback {
                        override fun onFileStart(name: String, totalBytes: Long?) {
                            notificationHelper.notifyFileStart(name, totalBytes)
                            runOnUiThread { tvCurrentFile.text = name }
                        }
                        override fun onFileProgress(name: String, readBytes: Long, totalBytes: Long?) {
                            val percent = if (totalBytes != null && totalBytes > 0) ((readBytes * 100 / totalBytes).toInt()) else 0
                            notificationHelper.notifyFileProgress(name, percent, readBytes, totalBytes)
                            runOnUiThread { progressBar.progress = percent }
                        }
                        override fun onFileComplete(name: String) {
                            notificationHelper.notifyFileComplete(name)
                        }
                        override fun onOverallProgress(percent: Int) {
                            notificationHelper.notifyOverallProgress(percent)
                            runOnUiThread { progressBar.progress = percent }
                        }
                    })
                    val out = manager.extractUriToDocumentTree(srcUri, destTree)
                    "解压完成: $out"
                } catch (e: Exception) {
                    "解压失败: ${e.message}"
                }
            }
            progressBar.visibility = View.GONE
            tvStatus.text = result
            tvCurrentFile.text = ""
            setUiEnabled(true)
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        btnPickFiles.isEnabled = enabled
        btnPickDest.isEnabled = enabled
        btnCompress.isEnabled = enabled
        btnExtract.isEnabled = enabled
    }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }
}
EOF

# ProgressCallback
cat > "$ROOT/app/src/main/java/com/example/unarchiver/ProgressCallback.kt" <<'EOF'
package com.example.unarchiver

interface ProgressCallback {
    fun onFileStart(name: String, totalBytes: Long?)
    fun onFileProgress(name: String, readBytes: Long, totalBytes: Long?)
    fun onFileComplete(name: String)
    fun onOverallProgress(percent: Int)
}
EOF

# NotificationHelper with more detailed notifications and ETA simple estimation
cat > "$ROOT/app/src/main/java/com/example/unarchiver/NotificationHelper.kt" <<'EOF'
package com.example.unarchiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "unarchiver_progress"
    private val notifIdOverall = 2001
    // per-file notifications use IDs derived from hash
    private val fileStartTimes = ConcurrentHashMap<String, Long>()
    private val fileBytesSeen = ConcurrentHashMap<String, Long>()

    init {
        try {
            val channel = android.app.NotificationChannel(channelId, "Unarchiver", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun baseIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun notifyFileStart(name: String, totalBytes: Long?) {
        fileStartTimes[name] = System.currentTimeMillis()
        fileBytesSeen[name] = 0L
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("开始: $name")
            .setContentText(if (totalBytes != null) "大小: ${totalBytes} bytes" else "大小未知")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, 0, totalBytes == null)
            .setContentIntent(baseIntent())
            .setOnlyAlertOnce(true)
        manager.notify(name.hashCode(), builder.build())
        // overall
        notifyOverallText("正在处理: $name")
    }

    fun notifyFileProgress(name: String, percent: Int, readBytes: Long, totalBytes: Long?) {
        fileBytesSeen[name] = readBytes
        val elapsed = max(1, System.currentTimeMillis() - (fileStartTimes[name] ?: System.currentTimeMillis()))
        val speed = readBytes * 1000 / elapsed // bytes per second
        val eta = if (totalBytes != null && speed > 0) ((totalBytes - readBytes) / speed) else -1L
        val etaText = if (eta >= 0) "ETA: ${eta}s" else ""
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("处理: $name")
            .setContentText("$percent% $etaText")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent.coerceIn(0,100), false)
            .setContentIntent(baseIntent())
            .setOnlyAlertOnce(true)
        manager.notify(name.hashCode(), builder.build())

        // update overall progress as well
        notifyOverallProgress(percent)
    }

    fun notifyFileComplete(name: String) {
        val read = fileBytesSeen[name] ?: 0L
        val elapsed = max(1, System.currentTimeMillis() - (fileStartTimes[name] ?: System.currentTimeMillis()))
        val speed = read * 1000 / elapsed
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("完成: $name")
            .setContentText("大小: ${read} bytes, 速率: ${speed} B/s")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(baseIntent())
            .setOnlyAlertOnce(true)
        manager.notify(name.hashCode(), builder.build())
    }

    fun notifyOverallProgress(percent: Int) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Unarchiver")
            .setContentText("总体进度: $percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent.coerceIn(0,100), false)
            .setOnlyAlertOnce(true)
            .setOngoing(percent in 0..99)
            .setContentIntent(baseIntent())
        manager.notify(notifIdOverall, builder.build())
        if (percent >= 100) manager.cancel(notifIdOverall)
    }

    fun notifyOverallText(text: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Unarchiver")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setContentIntent(baseIntent())
        manager.notify(notifIdOverall, builder.build())
    }
}
EOF

# ArchiveManager.kt (支持 junrar 解压, rar 压缩 via system 'rar' binary; large file threading)
cat > "$ROOT/app/src/main/java/com/example/unarchiver/ArchiveManager.kt" <<'EOF'
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
            var entry: ArchiveEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (entry.isDirectory) {
                    createDocumentDirTree(targetDir, name)
                } else {
                    callback?.onFileStart(name, entry.size)
                    // 若 entry 大文件则提交线程写入临时文件再copy
                    if (entry.size > largeFileThreshold) {
                        val tmpOut = File.createTempFile("big_entry", null, cacheDir)
                        val transferred = writeEntryToTemp(zis, tmpOut, name)
                        // 提交任务把 tmpOut 写回目标 SAF
                        executor.submit {
                            val fileDoc = createDocumentFileForPath(targetDir, name)
                                ?: throw IOException("创建目标文件失败: $name")
                            tmpOut.inputStream().use { fis2 ->
                                contentResolver.openOutputStream(fileDoc.uri).use { out ->
                                    if (out != null) {
                                        fis2.copyTo(out)
                                    } else throw IOException("无法打开输出流")
                                }
                            }
                            tmpOut.delete()
                            callback?.onFileComplete(name)
                        }
                    } else {
                        val fileDoc = createDocumentFileForPath(targetDir, name) ?: throw IOException("创建目标文件失败: $name")
                        contentResolver.openOutputStream(fileDoc.uri).use { out ->
                            if (out != null) {
                                val buffer = ByteArray(8192)
                                var readTotal = 0L
                                var read: Int
                                while (zis.read(buffer).also { read = it } > 0) {
                                    out.write(buffer, 0, read)
                                    readTotal += read
                                    callback?.onFileProgress(name, readTotal, entry.size)
                                }
                                callback?.onFileComplete(name)
                            } else throw IOException("无法打开输出流")
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun writeEntryToTemp(zis: InputStream, tmpOut: File, entryName: String): Long {
        tmpOut.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            var r: Int
            while (zis.read(buffer).also { r = it } > 0) {
                out.write(buffer, 0, r)
                total += r
                callback?.onFileProgress(entryName, total, null)
            }
            return total
        }
    }

    private fun extract7z(src: File, targetDir: DocumentFile) {
        RandomAccessFile(src, "r").use { raf ->
            val seven = SevenZFile(raf)
            var entry = seven.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                val name = entry.name
                if (entry.isDirectory) {
                    createDocumentDirTree(targetDir, name)
                } else {
                    callback?.onFileStart(name, entry.size)
                    if (entry.size > largeFileThreshold) {
                        val tmpOut = File.createTempFile("big_7z", null, cacheDir)
                        FileOutputStream(tmpOut).use { fos ->
                            var left = entry.size
                            while (left > 0) {
                                val toRead = min(buffer.size.toLong(), left).toInt()
                                val r = seven.read(buffer, 0, toRead)
                                if (r <= 0) break
                                fos.write(buffer, 0, r)
                                callback?.onFileProgress(name, (entry.size - left + r), entry.size)
                                left -= r
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
                                var left = entry.size
                                var readTotal = 0L
                                while (left > 0) {
                                    val toRead = min(buffer.size.toLong(), left).toInt()
                                    val r = seven.read(buffer, 0, toRead)
                                    if (r <= 0) break
                                    out.write(buffer, 0, r)
                                    readTotal += r
                                    left -= r
                                    callback?.onFileProgress(name, readTotal, entry.size)
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
                    while (cis.read(buffer).also { read = it } > 0) {
                        out.write(buffer, 0, read)
                        readTotal += read
                        callback?.onFileProgress(simpleName, readTotal, null)
                    }
                    callback?.onFileComplete(simpleName)
                } else throw IOException("无法打开输出流")
            }
        }
    }

    private fun extractFromArchiveInputStream(tar: ArchiveInputStream, targetDir: DocumentFile) {
        var entry: ArchiveEntry? = tar.nextEntry
        while (entry != null) {
            val name = entry.name
            if (entry.isDirectory) {
                createDocumentDirTree(targetDir, name)
            } else {
                val entrySize = if (entry is TarArchiveEntry) entry.size else null
                callback?.onFileStart(name, entrySize)
                if (entrySize != null && entrySize > largeFileThreshold) {
                    val tmpOut = File.createTempFile("big_tar", null, cacheDir)
                    tmpOut.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var r: Int
                        while (tar.read(buffer).also { r = it } > 0) {
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
                            while (tar.read(buffer).also { read = it } > 0) {
                                out.write(buffer, 0, read)
                                readTotal += read
                                callback?.onFileProgress(name, readTotal, entrySize)
                            }
                            callback?.onFileComplete(name)
                        } else throw IOException("无法打开输出流")
                    }
                }
            }
            entry = tar.nextEntry
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
                            while (input.read(buffer).also { r = it } > 0) {
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
            while (input.read(buffer).also { r = it } > 0) {
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
EOF

# FileUtil.kt
cat > "$ROOT/app/src/main/java/com/example/unarchiver/FileUtil.kt" <<'EOF'
package com.example.unarchiver

object FileUtil {
    fun sanitizeName(input: String): String {
        return input.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
EOF

# 单元测试
cat > "$ROOT/app/src/test/java/com/example/unarchiver/FileUtilTest.kt" <<'EOF'
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
EOF

# UI 测试（基础）
cat > "$ROOT/app/src/androidTest/java/com/example/unarchiver/MainActivityTest.kt" <<'EOF'
package com.example.unarchiver

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun ui_elements_displayed() {
        onView(withId(R.id.btn_pick_files)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_pick_dest)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_compress)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_extract)).check(matches(isDisplayed()))
    }
}
EOF

# create_icon.py
cat > "$ROOT/create_icon.py" <<'EOF'
#!/usr/bin/env python3
"""
生成简单的应用图标到 mipmap-* 目录
依赖: pillow
用法: python3 create_icon.py <res_root> <base_name>
"""
import sys, os
from PIL import Image, ImageDraw

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def make_icon(size, color=(98,0,238)):
    img = Image.new("RGBA", (size,size), (0,0,0,0))
    draw = ImageDraw.Draw(img)
    for i in range(size//2, 0, -1):
        ratio = i / (size/2)
        r = int(color[0]*ratio + 255*(1-ratio))
        g = int(color[1]*ratio + 255*(1-ratio))
        b = int(color[2]*ratio + 255*(1-ratio))
        bbox = [size//2 - i, size//2 - i, size//2 + i, size//2 + i]
        draw.ellipse(bbox, fill=(r,g,b,255))
    inset = size//6
    draw.ellipse([inset,inset,size-inset,size-inset], fill=(255,255,255,200))
    return img

def main():
    if len(sys.argv) < 3:
        print("Usage: create_icon.py <res_root> <base_name>")
        sys.exit(1)
    res_root = sys.argv[1]
    base = sys.argv[2]
    for folder, px in sizes.items():
        d = os.path.join(res_root, folder)
        os.makedirs(d, exist_ok=True)
        path = os.path.join(d, base + ".png")
        img = make_icon(px)
        img.save(path, format="PNG")
        print("Wrote", path)

if __name__ == "__main__":
    main()
EOF

chmod +x "$ROOT/create_icon.py"

# README
cat > "$ROOT/README.md" <<'EOF'
Unarchiver Android 项目骨架 (Kotlin) - RAR 集成、并行大文件、详细通知、测试

主要特性:
- compileSdk: 34, minSdk: 26, targetSdk: 31
- 支持解压: zip, 7z, rar (junrar), tar, tar.gz, tar.bz2, tar.xz, gz, bz2, xz
- 支持压缩: zip, 7z, tar, tar.gz, tar.bz2, tar.xz; RAR 压缩通过尝试系统 'rar' 二进制（若无则不可用）
- ArchiveManager 支持 ProgressCallback、逐文件进度、总体进度；遇到大文件（>5MB）自动使用线程池并行处理
- NotificationHelper 支持更详细的通知（单文件开始/进度/完成、总体进度、速率估算）
- 包含单元测试和简单 UI (Espresso) 测试
- 脚本会在结束时把整个项目打包为 ${ZIP_NAME}

说明:
- RAR 压缩受限：应用中不能直接用开源库安全地创建 RAR 文件（RAR 是专有格式）。脚本中实现的方案是把文件复制到临时目录并尝试调用系统 `rar` 二进制（需要在 PATH 中）。在 Android 设备上一般不可用；建议使用其它格式（zip/7z/tar.*）。
- 运行:
  1. 需要：bash, python3, pillow (pip install pillow), zip (系统工具)
  2. 运行：chmod +x setup_project.sh && ./setup_project.sh
  3. 运行脚本后会在本目录生成 ${ROOT} 以及压缩文件 ${ZIP_NAME}
  4. 打开 Android Studio 导入 ${ROOT}，同步 Gradle（需要 SDK 34）
EOF

# 打包为 zip
if command -v zip >/dev/null 2>&1; then
    echo "正在打包项目为 ${ZIP_NAME} ..."
    (cd "$(dirname "$ROOT")" || true)
    zip -r "$ZIP_NAME" "$ROOT" >/dev/null
    echo "已创建 ${ZIP_NAME} 在 $(pwd)/${ZIP_NAME}"
else
    echo "系统上未找到 zip 命令，跳过打包。请手动打包 $ROOT 目录。"
fi

echo "生成完成。"
echo "下一步："
echo "  cd $ROOT"
echo "  python3 create_icon.py app/src/main/res ic_launcher"
echo "在 Android Studio 中打开该目录并同步 Gradle（需要 SDK 34）。"
EOF