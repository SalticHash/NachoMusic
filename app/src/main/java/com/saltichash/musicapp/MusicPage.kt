package com.saltichash.musicapp

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.MoreExecutors
import java.io.File



enum class PlayMode {
    NORMAL,
    SHUFFLE,
    REPEAT_ONE,
    STOP_AFTER_END
}


private const val TAG = "MainActivityLogs"
class MusicPage : Fragment() {
    private lateinit var lvMusicEntries: RecyclerView
    private lateinit var playerView: PlayerView
    private lateinit var searchView: SearchView
    private lateinit var tvEmptySongs: TextView
    private lateinit var mediaController: MediaController
    lateinit var listAdapter: MusicEntryAdapter

    private var playMode: PlayMode = PlayMode.NORMAL
    private lateinit var exoPlayMode: ImageButton

    // Edit Song
    private var editSongLayout: ConstraintLayout? = null
    private lateinit var etTitle: EditText
    private lateinit var etUrl: EditText
    private lateinit var etAuthor: EditText
    private lateinit var etAlbum: EditText
    private lateinit var etTags: EditText
    private lateinit var btnEditSong: Button
    private lateinit var btnCancelEditSong: Button
    private lateinit var btnDeleteSong: Button
    private lateinit var btnCopyURL: Button
    private var currentEditedEntry: MusicEntry? = null
    private var currentEditedPosition: Int? = null

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
                mediaController = controllerFuture.get()
                playerView.setPlayer(mediaController)
                mediaController.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)

                        if (reason != MediaController.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                        if (playMode != PlayMode.STOP_AFTER_END) return

                        mediaController.pause()
                        mediaController.seekTo(mediaController.previousMediaItemIndex, 0)
                    }
                })
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_music_page, container, false)

        playerView = view.findViewById(R.id.playerView)
        playerView.requestFocus()
        @androidx.media3.common.util.UnstableApi
        playerView.showController()

        tvEmptySongs = view.findViewById(R.id.tvEmptySongs)
        tvEmptySongs.text = getString(R.string.empty_songs, MusicManager.appPath.absolutePath)
        searchView = view.findViewById(R.id.searchView)

        editSongLayout = view.findViewById(R.id.editSongLayout)
        btnEditSong = view.findViewById(R.id.btnEditSong)
        btnCancelEditSong = view.findViewById(R.id.btnCancelEditSong)
        btnCopyURL = view.findViewById(R.id.btnCopyURL)
        btnDeleteSong = view.findViewById(R.id.btnDeleteSong)
        exoPlayMode = view.findViewById(R.id.exo_play_mode)
        etTitle = view.findViewById(R.id.etTitle)
        etUrl = view.findViewById(R.id.etUrl)
        etAuthor = view.findViewById(R.id.etAuthor)
        etTags = view.findViewById(R.id.etTags)
        etAlbum = view.findViewById(R.id.etAlbum)

        exoPlayMode.setOnClickListener {
            playMode = when (playMode) {
                PlayMode.NORMAL       -> PlayMode.SHUFFLE
                PlayMode.SHUFFLE      -> PlayMode.REPEAT_ONE
                PlayMode.REPEAT_ONE   -> PlayMode.STOP_AFTER_END
                PlayMode.STOP_AFTER_END -> PlayMode.NORMAL
            }
            applyPlayMode(exoPlayMode)
        }


        val entries = MusicManager.musicEntries.values.toMutableList()

        listAdapter = MusicEntryAdapter(entries,
        object : MusicEntryAdapter.OnClickListener {
            override fun onClick(position: Int, model: MusicEntry) {
                if (model.error) {
                    Toast.makeText(context, R.string.not_found, Toast.LENGTH_SHORT).show()
                    return
                }
                if (mediaController.mediaItemCount <= 0) {
                    Toast.makeText(context, R.string.unknown_error, Toast.LENGTH_SHORT).show()
                    return
                }

                mediaController.seekTo(listAdapter.getPosition(model), 0)
                mediaController.play()
            }
        }, object : MusicEntryAdapter.OnLongClickListener {
            override fun onLongClick(position: Int, model: MusicEntry): Boolean {
                searchView.clearFocus()

                editSongLayout?.visibility = View.VISIBLE
                currentEditedEntry = model
                currentEditedPosition = listAdapter.getPosition(model)
                etUrl.setText(model.url)
                etTitle.setText(model.title)
                etAuthor.setText(model.author)
                etAlbum.setText(model.album)
                etTags.setText(model.tags.joinToString(", "))
                return true
            }
        })

        updateEmptySongNotice()

        btnDeleteSong.setOnClickListener {
            if (currentEditedEntry == null) return@setOnClickListener

            val builder = AlertDialog.Builder(context)
                .setTitle(R.string.delete_song_title)
                .setMessage(R.string.delete_song_msg)
                .setNegativeButton(R.string.cancel) { dialog, which -> return@setNegativeButton}

                .setPositiveButton(R.string.yes) { dialog, which ->
                    MusicManager.removeMusicEntry(currentEditedEntry!!)
                    removeMusic(currentEditedPosition!!)
                    closeEditView()
                }

            builder.create().show()
        }

        btnCopyURL.setOnClickListener {
            val ctx = requireContext()
            val clipboard: ClipboardManager = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val uri = Uri.parse(etUrl.text.toString())
            val data: ClipData = ClipData.newUri(ctx.contentResolver, "URL", uri)
            clipboard.setPrimaryClip(data)
        }

        btnCancelEditSong.setOnClickListener {
            closeEditView()
        }

        btnEditSong.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val author = etAuthor.text.toString().trim()
            val album = etAlbum.text.toString().trim()
            val tags = etTags.text.toString()
                .split(Regex("[.,;|]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() } as MutableList


            if (title.isBlank()) {
                etTitle.error = getString(R.string.song_form_error_title)
                return@setOnClickListener
            }

            if (currentEditedEntry == null) {
                etTitle.error = getString(R.string.unknown_error)
                return@setOnClickListener
            }

            val newEntry = currentEditedEntry!!
            newEntry.author = author
            newEntry.album = album
            newEntry.tags = tags
            newEntry.title = title

            MusicManager.editMusicEntry(newEntry.id, newEntry)
            editMusic(currentEditedPosition!!, newEntry)

            closeEditView()
        }


        // Set adapter
        lvMusicEntries = view.findViewById(R.id.lvMusicEntries)
        lvMusicEntries.layoutManager = LinearLayoutManager(context)
        lvMusicEntries.adapter = listAdapter


        // Include dividers
        val dividerItemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        lvMusicEntries.addItemDecoration(dividerItemDecoration)


        // Search
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(p0: String?): Boolean {
                listAdapter.filter(p0)
                return false
            }

            override fun onQueryTextSubmit(p0: String?): Boolean {
                listAdapter.filter(p0)
                return false
            }
        })

        return view
    }

    fun removeAllSongs() {
        requireActivity().runOnUiThread {
            mediaController.clearMediaItems()
        }
        listAdapter.removeAllEntries()
        updateEmptySongNotice()
    }
    fun updateEmptySongNotice() {
        requireActivity().runOnUiThread {
            tvEmptySongs.visibility = if (MusicManager.musicEntries.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
    fun closeEditView() {
        if (editSongLayout == null) return

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editSongLayout!!.windowToken, 0)

        searchView.clearFocus()
        requireActivity().runOnUiThread {
            editSongLayout!!.visibility = View.GONE
        }
        currentEditedEntry = null
        currentEditedPosition = null

        arrayOf(etUrl, etTitle, etAuthor, etAlbum).forEach {
            it.clearFocus()
            it.setText("")
        }
        arrayOf(btnEditSong, btnDeleteSong, btnCancelEditSong).forEach {
            it.clearFocus()
        }

        playerView.requestFocus()
    }

    fun addMusic(idx: Int, entry: MusicEntry) {
        updateEmptySongNotice()
        val realIdx = if (idx == -1) listAdapter.getRealItemCount() else idx
        val accessFile = File(MusicManager.musicPath, entry.filepath)

        if (!accessFile.exists()) {
            MusicManager.declareError(entry)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(entry.title)
            .setArtist(entry.author)
            .setAlbumTitle(entry.album)
            .build()


        val mediaIt = if (!entry.error) {
            MediaItem.Builder()
                .setMediaMetadata(metadata)
                .setUri(accessFile.toUri())
                .build()
        } else {
            MediaItem.Builder().setMediaMetadata(metadata).build()
        }


        requireActivity().runOnUiThread {
            listAdapter.addNewEntry(entry)
            mediaController.addMediaItem(realIdx, mediaIt)
        }
    }

    fun editMusic(idx: Int, newEntry: MusicEntry) {
        val accessFile = File(MusicManager.musicPath, newEntry.filepath)

        val metadata = MediaMetadata.Builder()
            .setTitle(newEntry.title)
            .setArtist(newEntry.author)
            .setAlbumTitle(newEntry.album)
            .build()

        val mediaIt = MediaItem.Builder()
            .setUri(accessFile.toUri())
            .setMediaMetadata(metadata)
            .build()

        val oldPosition = mediaController.currentPosition
        val editingCurrent = mediaController.currentMediaItemIndex == idx

        requireActivity().runOnUiThread {
            listAdapter.editEntry(idx, newEntry)
            mediaController.replaceMediaItem(idx, mediaIt)
        }

        if (editingCurrent) {
            mediaController.seekTo(idx, oldPosition)
        }
    }

    fun removeMusic(idx: Int) {
        requireActivity().runOnUiThread {
            listAdapter.removeEntry(idx)
            mediaController.removeMediaItem(idx)
        }
        updateEmptySongNotice()
    }


    private fun applyPlayMode(btn: ImageButton) {
        when (playMode) {

            PlayMode.NORMAL -> {
                mediaController.shuffleModeEnabled = false
                mediaController.repeatMode = MediaController.REPEAT_MODE_OFF
                btn.setImageResource(R.drawable.ic_shuffle_off)
            }

            PlayMode.SHUFFLE -> {
                mediaController.shuffleModeEnabled = true
                mediaController.repeatMode = MediaController.REPEAT_MODE_OFF
                btn.setImageResource(R.drawable.ic_shuffle)
            }

            PlayMode.REPEAT_ONE -> {
                mediaController.shuffleModeEnabled = false
                mediaController.repeatMode = MediaController.REPEAT_MODE_ONE
                btn.setImageResource(R.drawable.ic_repeat_all)
            }

            PlayMode.STOP_AFTER_END -> {
                mediaController.shuffleModeEnabled = false
                mediaController.repeatMode = MediaController.REPEAT_MODE_OFF
                btn.setImageResource(R.drawable.ic_playmode_once)
            }
        }
    }

}