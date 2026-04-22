package com.saltichash.musicapp


import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.saltichash.musicapp.databinding.ActivityMainBinding
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import java.util.Calendar


private const val TAG = "MainActivityLogs"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var activeFragment: Fragment
    private lateinit var musicPage: MusicPage
    private lateinit var playerPage: PlayerPage
    private lateinit var downloadPage: DownloadPage
    private lateinit var settingsPage: SettingsPage

    fun switchTo(alias: String) {
        val pm = packageManager
        val default = ComponentName(this, "com.saltichash.musicapp.AliasDefault")
        val christmas = ComponentName(this, "com.saltichash.musicapp.AliasChristmas")

        val state = pm.getComponentEnabledSetting(christmas)
        val isChristmasEnabled =
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT


        if (alias == "christmas" && isChristmasEnabled) return
        if (alias == "default" && !isChristmasEnabled) return

        pm.setComponentEnabledSetting(
            default,
            if (alias == "default") PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        pm.setComponentEnabledSetting(
            christmas,
            if (alias == "christmas") PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Update Icon
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        if (month == Calendar.DECEMBER) {
            switchTo("christmas")
        } else {
            switchTo("default")
        }

        // Make UI extend over notifications and phone's buttons
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MusicManager.initPaths(binding.root.context)
        MusicManager.loadMusicFile()

        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Aria2c.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "failed to initialize youtubedl-android", e)
        }

        musicPage = MusicPage()
        downloadPage = DownloadPage()
        settingsPage = SettingsPage()
        playerPage = PlayerPage()

        if (savedInstanceState == null) {
            changeTab(R.id.musicPage)
        } else {
            val selectedTab = savedInstanceState.getInt("selectedTab")
            changeTab(selectedTab)
        }


        binding.navbar.setOnItemSelectedListener {
            changeTab(it.itemId, false)
            true
        }

        // Avoid errors of UI overflowing phone's buttons / UI
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        onNewIntent(intent)


    }

    private fun changeTab(id: Int, setNav: Boolean = true) {
        if (id != R.id.musicPage) {
            musicPage.closeEditView()
        }
        when (id) {
            R.id.playerPage -> replaceFragment(playerPage, "PlayerPage")
            R.id.musicPage -> replaceFragment(musicPage, "MusicPage")
            R.id.downloadPage -> replaceFragment(downloadPage, "DownloadPage")
            R.id.settingsPage -> replaceFragment(settingsPage, "SettingsPage")
            else -> {}
        }

        if (!setNav) return
        binding.navbar.setSelectedItemId(id)
    }

    private fun replaceFragment(fragment: Fragment, tag: String) {
        val transaction = supportFragmentManager.beginTransaction()

        // Get existing fragment
        val musicPagePlayer = (tag == "PlayerPage")
        val realTag = if (tag == "PlayerPage") "MusicPage" else tag;
        val existingFragment = supportFragmentManager.findFragmentByTag(realTag)
        if (existingFragment == null) {
            transaction.add(R.id.fragment_container, fragment, realTag)
        }

        // Hide current fragment
        if (::activeFragment.isInitialized && activeFragment.isAdded) {
            transaction.hide(activeFragment)
        }

        // Show the target fragment (existing or just added)
        transaction.show(existingFragment ?: fragment)

        transaction.commitNow()

        // Set to existing if existed, else fragment
        activeFragment = existingFragment ?: fragment
        (activeFragment as? MusicPage)?.showPlayer(musicPagePlayer)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selectedTab", binding.navbar.selectedItemId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        onSharedIntent(intent)
    }

    private fun onSharedIntent(intent: Intent) {
        val receivedText: String? =
            intent.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        if (!receivedText.isNullOrBlank()) {
            // Switch to download tab first
            changeTab(R.id.downloadPage, true)

            // Then post on the active fragment's view (downloadPage) after layout
            binding.fragmentContainer.post {
                val dp: DownloadPage = downloadPage
                dp.sharedUrl(receivedText)
            }
        }
    }


}
