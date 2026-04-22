package com.saltichash.musicapp

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.MultiAutoCompleteTextView
import android.widget.ProgressBar
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
import com.saltichash.musicapp.MusicEntryAdapter.ViewHolder
import java.io.File
import java.util.Locale


enum class PlayMode {
    NORMAL,
    SHUFFLE,
    REPEAT_ONE,
    STOP_AFTER_END
}


private const val TAG = "MainActivityLogs"
class MusicPage : Fragment() {
    private lateinit var lvMusicEntries: RecyclerView
    private lateinit var searchView: MultiAutoCompleteTextView
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

    // Music Player
    private lateinit var playerView: PlayerView
    private lateinit var albumImage: ImageView
    private lateinit var songTitle: TextView
    private lateinit var currentSongEntry: ConstraintLayout
    private lateinit var currentSongProgress: ProgressBar
    private lateinit var musicPlayerLayout: ConstraintLayout

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

                fun updateMetadata() {
                    val metadata = mediaController.mediaMetadata
                    val item = listAdapter.getRealItem(mediaController.currentMediaItemIndex)
                    changedMetadata(metadata, item)
                }
                mediaController.addListener(object : Player.Listener {
                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        updateMetadata()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            startProgressUpdates()
                        } else {
                            stopProgressUpdates()
                        }
                    }
                })
                updateMetadata()

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
        musicPlayerLayout = view.findViewById(R.id.musicPlayerLayout)

        songTitle = musicPlayerLayout.findViewById(R.id.songTitle)
        albumImage = musicPlayerLayout.findViewById(R.id.albumImage)
        currentSongEntry = view.findViewById(R.id.currentSongEntry)
        currentSongProgress = currentSongEntry.findViewById(R.id.progressBar)
        currentSongProgress.visibility = View.VISIBLE

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
        val aaStr:ArrayAdapter<String> = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item,
            mutableListOf())
        searchView.setAdapter(aaStr);
        searchView.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
        searchView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                listAdapter.filter(text.toString())
            }
        })
        searchView.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) return@setOnFocusChangeListener
            val tags = MusicManager.musicEntries.values
                .flatMap { it.tags }
                .distinct()

            aaStr.clear()
            aaStr.addAll(tags)
            aaStr.notifyDataSetChanged()
        }

        currentSongEntry.setOnClickListener {
            val idx = mediaController.currentMediaItemIndex
            val entry = listAdapter.getRealItem(idx)
            if (!listAdapter.containsEntry(entry)) return@setOnClickListener
            val practicalPosition = listAdapter.getFilteredPosition(entry)

            val layoutManager = lvMusicEntries.layoutManager as LinearLayoutManager
            val first = layoutManager.findFirstVisibleItemPosition()
            val last = layoutManager.findLastVisibleItemPosition()

            if (practicalPosition in first..last) {
                lvMusicEntries.post {
                    val holder = lvMusicEntries.findViewHolderForAdapterPosition(practicalPosition)
                    val view = holder?.itemView ?: return@post
                    view.isPressed = true
                    view.postDelayed({ view.isPressed = false }, 150)
                }
            } else {
                lvMusicEntries.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            rv.removeOnScrollListener(this)

                            val holder = lvMusicEntries.findViewHolderForAdapterPosition(practicalPosition)
                            val view = holder?.itemView ?: return

                            view.isPressed = true
                            view.postDelayed({ view.isPressed = false }, 150)
                        }
                    }
                })
            }


            lvMusicEntries.smoothScrollToPosition(practicalPosition)


        }


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

    private fun changedMetadata(meta: MediaMetadata, entry: MusicEntry?) {
        if (entry != null) setCurrentSongEntry(entry)
        songTitle.text = entry?.title
        val data = meta.artworkData
        if (data?.isNotEmpty() == true) {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            albumImage.setImageBitmap(bitmap)
        }
        val duration = mediaController.duration
        currentSongProgress.max = duration.toInt()
        currentSongProgress.progress = 0
    }

    private val currentSongProgressHandler = Handler(Looper.getMainLooper())

    private val updateProgress = object : Runnable {
        override fun run() {
            val position = mediaController.currentPosition
            currentSongProgress.progress = position.toInt()

            currentSongProgressHandler.postDelayed(this, 500)
        }
    }
    private fun startProgressUpdates() {
        currentSongProgressHandler.post(updateProgress)
    }
    private fun stopProgressUpdates() {
        currentSongProgressHandler.removeCallbacks(updateProgress)
    }

    // Replace the contents of a view (invoked by the layout manager)
    fun setCurrentSongEntry(entry: MusicEntry) {
        val tvTitle: TextView = currentSongEntry.findViewById(R.id.tvTitle)
        val tvDesc: TextView = currentSongEntry.findViewById(R.id.tvDesc)
        tvDesc.visibility = View.GONE
        val tvDuration: TextView = currentSongEntry.findViewById(R.id.tvDuration)
        val icError: ImageView = currentSongEntry.findViewById(R.id.icError)

        val viewHolder = currentSongEntry
        tvTitle.text = if (entry.title.isBlank()) {
            viewHolder.context.getString(R.string.unnamed_song)
        } else {
            entry.title
        }

        if (entry.error) {
            icError.visibility = View.VISIBLE
        }

        val secondsTotal = entry.durationMsec / 1000
        val minutes = secondsTotal / 60
        val seconds = secondsTotal % 60
        tvDuration.text =
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    fun showPlayer(show: Boolean) {
        if (!this::musicPlayerLayout.isInitialized) return
        if (show) {
            musicPlayerLayout.visibility = View.VISIBLE
            lvMusicEntries.visibility = View.GONE
            searchView.visibility = View.GONE
            currentSongEntry.visibility = View.GONE
        } else {
            musicPlayerLayout.visibility = View.GONE
            lvMusicEntries.visibility = View.VISIBLE
            searchView.visibility = View.VISIBLE
            currentSongEntry.visibility = View.VISIBLE
        }
    }

}