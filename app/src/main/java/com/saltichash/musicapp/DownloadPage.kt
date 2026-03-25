package com.saltichash.musicapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.launch

import kotlinx.coroutines.Dispatchers
import java.util.Locale


private const val TAG = "MainActivityLogs"
class DownloadPage : Fragment() {


    private var downloadService: DownloadService? = null
    private var bound = false

    // ServiceConnection
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DownloadService.LocalBinder
            downloadService = binder.getService()
            bound = true

            // Set the listener to receive updates
            downloadService?.setDownloadListener(object : DownloadService.DownloadListener         {
                override fun onProcessStart() {
                    downloadProgress.isIndeterminate = true
                    setCurrentlyDownloading(true)
                }
                override fun onProcessEnd() {
                    Handler(Looper.getMainLooper()).postDelayed({
                        setCurrentlyDownloading(false)
                    }, 1000)
                }
                override fun onDownloadFinished(entry: MusicEntry) {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), getString(R.string.download_success, entry.title), Toast.LENGTH_SHORT).show()
                    }
                    // Add entry to visual list
                    val musicPage: MusicPage? = activity?.supportFragmentManager?.findFragmentByTag("MusicPage") as MusicPage
                    musicPage?.addMusic(-1, entry)
                }
                override fun onBatchDownloadedFinished() {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), R.string.batch_success, Toast.LENGTH_LONG).show()
                    }
                }
                override fun onError(error: Throwable) {
                    setCurrentlyDownloading(false)

                    Log.e(TAG, "Download failed", error)
                    activity?.runOnUiThread {
                        tvDownloadInfo.text = error.message
                        Toast.makeText(requireContext(), R.string.download_failure, Toast.LENGTH_LONG).show()
                    }
                }
                override fun onProgress(progress: Float, etaInSeconds: Long, msg: String) {
                    Log.i(TAG, "$progress% (ETA $etaInSeconds seconds)\n$msg")
                    setDownloadProgress(progress, etaInSeconds, msg)
                    setCurrentlyDownloading(true)
                }
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            downloadService = null
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to the service
        val intent = Intent(requireContext(), DownloadService::class.java)
        requireActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        // Unbind
        if (bound) {
            requireActivity().unbindService(connection)
            bound = false
        }
    }


    private var currentlyDownloading: Boolean = true

    private lateinit var tvDownloadInfo: TextView
    private lateinit var tvDownloadPercent: TextView
    private lateinit var tvDownloaderInfo: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var btnDownloadSong: Button
    private lateinit var btnUpdateDownloader: Button

    private lateinit var btnAutofillTitle: ToggleButton
    private lateinit var btnAutofillAuthor: ToggleButton
    private lateinit var btnAutofillAlbum: ToggleButton
    private lateinit var etTitle: EditText
    private lateinit var etUrl: EditText
    private lateinit var etAuthor: EditText
    private lateinit var etAlbum: EditText
    private lateinit var etTags: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_download_page, container, false)

        btnDownloadSong = view.findViewById(R.id.btnDownloadSong)
        btnUpdateDownloader = view.findViewById(R.id.btnUpdateDownloader)
        btnAutofillTitle = view.findViewById(R.id.btnAutofillTitle)
        btnAutofillAuthor = view.findViewById(R.id.btnAutofillAuthor)
        btnAutofillAlbum = view.findViewById(R.id.btnAutofillAlbum)

        btnAutofillTitle.setOnClickListener{toggleButton(btnAutofillTitle, etTitle)}
        btnAutofillAuthor.setOnClickListener{toggleButton(btnAutofillAuthor, etAuthor)}
        btnAutofillAlbum.setOnClickListener{toggleButton(btnAutofillAlbum, etAlbum)}

        tvDownloadInfo = view.findViewById(R.id.tvDownloadInfo)
        tvDownloaderInfo = view.findViewById(R.id.tvDownloaderInfo)
        tvDownloadPercent = view.findViewById(R.id.tvDownloadPercent)
        downloadProgress = view.findViewById(R.id.downloadProgress)

        val version = YoutubeDL.getInstance().versionName(context)
        tvDownloaderInfo.text = getString(R.string.downloader_update_info_no_state, version)
        etTitle = view.findViewById(R.id.etTitle)
        etUrl = view.findViewById(R.id.etUrl)
        etAuthor = view.findViewById(R.id.etAuthor)
        etAlbum = view.findViewById(R.id.etAlbum)
        etTags = view.findViewById(R.id.etTags)

        // Clear errors after typing
        etTitle.addTextChangedListener {etTitle.error = null}
        etUrl.addTextChangedListener {etUrl.error = null}

        currentlyDownloading = savedInstanceState?.getBoolean("autofillTitle") == true
        setCurrentlyDownloading(currentlyDownloading)

        btnUpdateDownloader.setOnClickListener {
            if (!checkForInternet()) return@setOnClickListener
            val ytdlp = YoutubeDL.getInstance()

            val ctx = requireContext()
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                setCurrentlyDownloading(true)

                val status = ytdlp.updateYoutubeDL(ctx)
                val statusName = getString(
                    if (status == YoutubeDL.UpdateStatus.DONE) {R.string.downloader_done}
                    else {R.string.downloader_up_to_date}
                )
                val version = ytdlp.versionName(ctx)

                requireActivity().runOnUiThread {
                    tvDownloaderInfo.text = getString(R.string.downloader_update_info, version, statusName)
                }

                setCurrentlyDownloading(false)
            }



            setCurrentlyDownloading(true)
        }

        btnDownloadSong.setOnClickListener {
            if (!checkForInternet()) return@setOnClickListener

            val url = etUrl.text.toString().trim()
            val title = etTitle.text.toString().trim()
            val author = etAuthor.text.toString().trim()
            val album = etAlbum.text.toString().trim()
            val tags = etTags.text.toString().trim()

            if (!URLUtil.isValidUrl(url)) {
                etUrl.error = getString(R.string.song_form_error_url)
                return@setOnClickListener
            }
            if (title.isBlank() && !btnAutofillTitle.isChecked) {
                etTitle.error = getString(R.string.song_form_error_title)
                return@setOnClickListener
            }

            downloadMusic(url, title, author, album, tags)
        }

        savedInstanceState?.let {
            btnAutofillTitle.isChecked = it.getBoolean("autofillTitleChecked")
            btnAutofillAuthor.isChecked = it.getBoolean("autofillAuthorChecked")
            btnAutofillAlbum.isChecked = it.getBoolean("autofillAlbumChecked")
        }
        toggleButton(btnAutofillAlbum, etAlbum)
        toggleButton(btnAutofillAuthor, etAuthor)
        toggleButton(btnAutofillTitle, etTitle)

        return view
    }

    private fun checkForInternet(): Boolean {
        if (isNetworkAvailable(requireContext())) return true
        Toast.makeText(context, R.string.no_internet, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun toggleButton(button: ToggleButton, editText: EditText) {
        if (button.isChecked) {
            editText.isEnabled = false
            editText.setText(R.string.autofill)
        } else {
            editText.isEnabled = true
            editText.setText("")
        }
    }

    private fun downloadMusic(url: String, title: String, author: String, album: String, tags: String) {
        // %(ext)s = (automatic detect extension) -> YoutubeDL
        val intent = Intent(requireContext(), DownloadService::class.java).apply {
            putExtra("url", url)
            putExtra("title", if (btnAutofillTitle.isChecked) null else title)
            putExtra("author", if (btnAutofillAuthor.isChecked) null else author)
            putExtra("album", if (btnAutofillAlbum.isChecked) null else album)
            putExtra("tags", tags)
        }

        ContextCompat.startForegroundService(requireContext(), intent)
    }

    fun sharedUrl(url: String) {
        if (currentlyDownloading) {
            Toast.makeText(context, R.string.download_wait, Toast.LENGTH_SHORT).show()
            return
        }
        etUrl.setText(url)
    }

    private fun setCurrentlyDownloading(downloading: Boolean) {
        currentlyDownloading = downloading
        val controlsEnabled = !downloading
        val progressVisible = downloading
        requireActivity().runOnUiThread {
            etTitle.isEnabled = controlsEnabled && !btnAutofillTitle.isChecked
            etAuthor.isEnabled = controlsEnabled && !btnAutofillAuthor.isChecked
            etAlbum.isEnabled = controlsEnabled && !btnAutofillAlbum.isChecked
            etTags.isEnabled = controlsEnabled

            btnAutofillTitle.isEnabled = controlsEnabled
            btnAutofillAuthor.isEnabled = controlsEnabled
            btnAutofillAlbum.isEnabled = controlsEnabled

            btnDownloadSong.isEnabled = controlsEnabled
            btnUpdateDownloader.isEnabled = controlsEnabled


            if (progressVisible) {
                //downloadProgress.isIndeterminate = indeterminate
                tvDownloadInfo.visibility = View.VISIBLE
                tvDownloadPercent.visibility = View.VISIBLE
                downloadProgress.visibility = View.VISIBLE
            } else {
                tvDownloadInfo.visibility = View.INVISIBLE
                tvDownloadInfo.text = ""

                tvDownloadPercent.visibility = View.INVISIBLE
                tvDownloadPercent.text = ""

                downloadProgress.visibility = View.INVISIBLE
                downloadProgress.progress = 0
            }
        }
    }


    private fun setDownloadProgress(progress: Float, etaInSeconds: Long, message: String) {
        requireActivity().runOnUiThread {
            if (progress >= 0) {
                downloadProgress.isIndeterminate = false
                tvDownloadPercent.text = String.format(Locale.getDefault(), "%.2f%%", progress)

                downloadProgress.progress = ((progress / 100.0) * downloadProgress.max).toInt()

                tvDownloadInfo.text = getString(R.string.download_time_left, etaInSeconds, message)
            } else {
                tvDownloadInfo.text = message
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val nw    = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            //for other device who are able to connect with Ethernet
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            //for check internet over Bluetooth
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
            else -> false
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.let {
            it.putBoolean("currentlyDownloading", currentlyDownloading)
            it.putBoolean("autofillAlbumChecked", btnAutofillAlbum.isChecked)
            it.putBoolean("autofillAuthorChecked", btnAutofillAuthor.isChecked)
            it.putBoolean("autofillTitleChecked", btnAutofillTitle.isChecked)
        }
    }

}