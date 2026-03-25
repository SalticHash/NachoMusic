package com.saltichash.musicapp

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    // Create your Player and MediaSession in the onCreate lifecycle event
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()

        MusicManager.musicEntries.forEach { id, entry ->
            addMediaItem(-1, entry)
        }
    }

    fun addMediaItem(idx: Int, entry: MusicEntry) {
        val realIdx = if (idx == -1) player.mediaItemCount else idx
        val accessFile = File(MusicManager.musicPath, entry.filepath)
        if (entry.error) {
            return
        }
        if (!accessFile.exists()) {
            MusicManager.declareError(entry)
            return
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(entry.title)
            .setArtist(entry.author)
            .setAlbumTitle(entry.album)
            .build()

        val mediaIt = MediaItem.Builder()
            .setUri(accessFile.toUri())
            .setMediaMetadata(metadata)
            .build()

        player.addMediaItem(realIdx, mediaIt)
    }

    // Remember to release the player and media session in onDestroy
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    // This example always accepts the connection request
    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession
}