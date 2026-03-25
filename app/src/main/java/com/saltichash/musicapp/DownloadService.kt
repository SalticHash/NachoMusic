package com.saltichash.musicapp

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID

private const val TAG = "MainActivityLogs"

class DownloadService : Service() {
    private val notificationId: Int = 1
    private val successNotificationId: Int = 2
    private val errorNotificationId: Int = 3
    val notificationChannelId = "DOWNLOAD_CHANNEL_V2"

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            notificationChannelId,
            getString(R.string.downloads_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.downloads_channel_desc)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    // Binder for clients that bind to the service
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private lateinit var ytdlp: YoutubeDL
    private var lastDownloadedSong: String = ""
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        ytdlp = YoutubeDL.getInstance()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        lastDownloadedSong = ""

        val url: String = intent?.getStringExtra("url") ?: ""
        val title: String? = intent?.getStringExtra("title")
        val author: String? = intent?.getStringExtra("author")
        val album: String? = intent?.getStringExtra("album")
        val tags: String = intent?.getStringExtra("tags") ?: ""


        Thread {
            downloadMusic(url, title, author, album, tags)
            stopSelf(startId)
        }.start()

        return START_NOT_STICKY
    }

    private fun createRequest(url: String): YoutubeDLRequest {
        val downloadDir = File(MusicManager.musicPath, "%(title)s.%(ext)s")

        val request = YoutubeDLRequest(url)

        // Format
        request.addOption("--format", "ba[acodec^=aac]/ba[acodec^=mp4a.40.]/ba/b")
        request.addOption("--extract-audio")
        request.addOption("--audio-format", "aac")

        // Metadata
        request.addOption("--embed-thumbnail")
        request.addOption("--embed-metadata")

        // Get out path (for dynamic extension)
        request.addOption("--newline")
        request.addOption("--progress")
        request.addOption("--print", "after_move:%(webpage_url)s\t%(filepath)s")

        // Set out path
        request.addOption("--output", downloadDir.absolutePath)

        return request
    }

    private fun downloadMusic(url: String, title: String?, author: String?, album: String?, tags: String) {
        val request = createRequest(url)

        var downloadCount = 0

        try {
            // Download started notification indeterminate
            listener?.onProcessStart()

            ytdlp.execute(request) { progress: Float, etaInSeconds: Long, outMsg: String ->
                val outMsgWasEntryPath = createDownloadedEntry(outMsg, title, author, album, tags)
                var notice = outMsg

                if (outMsgWasEntryPath) {
                    notice = ""
                    downloadCount++
                }

                listener?.onProgress(progress, etaInSeconds, notice)
                val nm = getSystemService(NotificationManager::class.java)
                val notification = createDownloadNotification(progress)
                nm.notify(notificationId, notification)
            }
        } catch (error: YoutubeDLException) {
            listener?.onError(error)
            createFailureNotification(error.message ?: "No error")
            return
        }

        if (downloadCount > 1) {
            listener?.onBatchDownloadedFinished()
            createSuccessNotification(true)
        } else {
            createSuccessNotification(false, lastDownloadedSong)
        }

        listener?.onProcessEnd()
    }


    private fun createDownloadedEntry(outMsg: String, title: String?, author: String?, album: String?, tags: String): Boolean {
        val parts = outMsg.split("\t", limit = 2)
        if (parts.size < 2) {
            return false
        }

        var (videoUrl, filepath) = parts
        val outFile = File(filepath)
        if (!outFile.exists() || !outFile.isFile) {
            return false
        }

        if (!URLUtil.isValidUrl(videoUrl)) {
            videoUrl = ""
        }

        val filename = outFile.name

        val infoEntry = MusicManager.getMediaMetadataAsEntry(outFile) // Values gotten for video meta

        // If overrides use them, else default meta values are used
        val autoTitle = (title ?: infoEntry?.title) ?: ""
        val autoAuthor = (author ?: infoEntry?.author) ?: ""
        val autoAlbum = (album ?: infoEntry?.album) ?: ""
        // This one is important!
        val durationMsec = infoEntry?.durationMsec ?: 0L

        Log.i(TAG, infoEntry.toString())

        val parsedTags = tags
            .split(Regex("[.,;|\\s]+"))
            .filter { it.isNotBlank() } as MutableList

        // Make entry by combining all the gotten components
        val entry: MusicEntry = MusicEntry(
            UUID.randomUUID().toString(),
            videoUrl, filename, autoTitle, autoAuthor, autoAlbum, parsedTags, durationMsec, false
        )

        // Add entry to save
        MusicManager.addMusicEntry(entry)

        listener?.onDownloadFinished(entry)
        lastDownloadedSong = entry.title
        return true
    }

    interface DownloadListener {
        fun onProcessStart()
        fun onProcessEnd()

        fun onDownloadFinished(entry: MusicEntry)
        fun onBatchDownloadedFinished()
        fun onError(msg: Throwable)
        fun onProgress(progress: Float, etaInSeconds: Long, msg: String)
    }

    private var listener: DownloadListener? = null

    fun setDownloadListener(l: DownloadListener) { listener = l }


    override fun onDestroy() {
        super.onDestroy()

        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(notificationId)
        // Clean up resources here
    }

    private fun startForegroundService() {
        val notification: Notification = createDownloadNotification(-1.0f)

        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )
    }

    private fun createDownloadNotification(progress: Float): Notification {
        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentText(getString(R.string.download_info))
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (progress < 0) {
            builder.setProgress(0, 0, true) // spinning progress
            builder.setContentTitle(getString(R.string.download_starting))
        } else {
            builder.setProgress(100, progress.toInt(), false) // progress: 0–100
            builder.setContentTitle(getString(R.string.download_progress, progress.toInt()))
        }


        return builder.build()

    }

    private fun createInfoNotification(id: Int, title: String, text: String, icon: Int) {
        val completeNotification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(id, completeNotification)

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createSuccessNotification(batch: Boolean, title: String = "") {
        createInfoNotification(
            successNotificationId,
            getString(R.string.download_finished),
                if (batch) getString(R.string.batch_success)
                else getString(R.string.download_success, title),
            R.drawable.ic_download_done
        )
    }

    private fun createFailureNotification(error: String) {
        createInfoNotification(
            errorNotificationId,
            getString(R.string.download_failure),
            error,
            R.drawable.ic_download_error
        )
    }

}
