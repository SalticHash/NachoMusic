package com.saltichash.musicapp

import android.content.ComponentName
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.MoreExecutors

class PlayerPage : Fragment() {
    private lateinit var playerView: PlayerView
    private lateinit var albumImage: ImageView
    private lateinit var songTitle: TextView

    override fun onStart() {
        super.onStart()
        val ctx = requireContext()
        val sessionToken = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(ctx, sessionToken).buildAsync()

        controllerFuture.addListener(
            {
                // Call controllerFuture.get() to retrieve the MediaController.
                // MediaController implements the Player interface, so it can be
                // attached to the PlayerView UI component.
                playerView.setPlayer(controllerFuture.get())
                val player = playerView.player
                if (player != null) {
                    player.addListener(object : Player.Listener {
                        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                            changedMetadata(mediaMetadata)
                        }
                    })
                    if (player.currentMediaItem != null)
                        changedMetadata(player.mediaMetadata)
                }

            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_player_page, container, false)
        playerView = view.findViewById(R.id.playerView)
        playerView.requestFocus()
        @androidx.media3.common.util.UnstableApi
        playerView.showController()

        songTitle = view.findViewById(R.id.songTitle)
        albumImage = view.findViewById(R.id.albumImage)


        return view
    }

    fun changedMetadata(meta: MediaMetadata) {
        songTitle.text = meta.title ?: "Unknown"
        val data = meta.artworkData
        if (data?.isNotEmpty() == true) {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            albumImage.setImageBitmap(bitmap)
        }
    }
}