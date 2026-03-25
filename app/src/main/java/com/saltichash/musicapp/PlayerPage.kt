package com.saltichash.musicapp

import android.content.ComponentName
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.MoreExecutors

class PlayerPage : Fragment() {
    private lateinit var playerView: PlayerView

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

        return view
    }
}