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
