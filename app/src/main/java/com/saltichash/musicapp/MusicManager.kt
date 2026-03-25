package com.saltichash.musicapp

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.FragmentActivity
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileFilter
import java.util.UUID

private const val TAG = "MainActivityLogs"


@Serializable
data class MusicEntry(
    val id: String = "",
    var url: String = "",
    var filepath: String = "",

    var title: String = "",
    var author: String = "",
    var album: String = "",
    var tags: MutableList<String> = mutableListOf(),

    var durationMsec: Long = 0L,
    var error: Boolean = false
)


typealias MusicEntryList = MutableMap<String, MusicEntry>
object MusicManager {
    lateinit var musicEntries: MusicEntryList

    lateinit var appPath: File
    lateinit var settingsJson: File
    lateinit var musicJson: File
    lateinit var musicPath: File

    var pathsInitialized: Boolean = false

    fun entryFromEntry(
        entry: MusicEntry,
        id: String = "",
        url: String = "",
        filepath: String = "",
        title: String = "",
        author: String = "",
        album: String = "",
        tags: MutableList<String> = mutableListOf(),
        durationMsec: Long = 0L,
        error: Boolean = false
    ): MusicEntry {
        return MusicEntry(
            id = if (entry.id.isNotBlank()) entry.id else id,
            url = if (entry.url.isNotBlank()) entry.url else url,
            filepath = if (entry.filepath.isNotBlank()) entry.filepath else filepath,
            title = if (entry.title.isNotBlank()) entry.title else title,
            author = if (entry.author.isNotBlank()) entry.author else author,
            album = if (entry.album.isNotBlank()) entry.album else album,
            tags = if (entry.tags.isNotEmpty()) entry.tags else tags,
            durationMsec = if (entry.durationMsec != 0L) entry.durationMsec else durationMsec,
            error = entry.error || error
        )
    }


    fun initPaths(context: Context) {
        appPath = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("External storage not available")
        settingsJson = File(appPath, "settings.json")
        musicJson = File(appPath, "music.json")
        musicPath = File(appPath, "music")

        pathsInitialized = true
        createNecessaryFiles()
    }

    private fun createNecessaryFiles() {
        if (!appPath.exists()) appPath.mkdirs()
        if (!musicPath.exists()) musicPath.mkdirs()
        if (!settingsJson.exists()) settingsJson.createNewFile()
        if (!musicJson.exists()) musicJson.createNewFile()
    }

    fun loadMusicFile() {
        createNecessaryFiles()
        val musicJsonString = musicJson.readText()

        val jsonSettings = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        musicEntries = try {
            if (musicJsonString.isBlank()) {
                mutableMapOf()
            } else {
                jsonSettings.decodeFromString<MusicEntryList>(musicJsonString)
            }
        } catch (e: SerializationException) {
            e.printStackTrace()
            mutableMapOf()
        }

        // Fix errors
        musicEntries.forEach { id, entry ->
            if (entry.durationMsec <= 0) {
                val out = File(musicPath, entry.filepath)
                val durationMsec = getMediaDuration(out)
                entry.durationMsec = durationMsec
            }
            if (entry.id.isBlank()) {
                musicEntries[id] = entryFromEntry(entry, id = id)
            }
        }

        saveMusicFile()
    }

    fun removeMusicEntry(entry: MusicEntry) {
        musicEntries.remove(entry.id)
        saveMusicFile()
    }

    fun addMusicEntry(entry: MusicEntry) {
        musicEntries[entry.id] = entry
        saveMusicFile()
    }

    fun editMusicEntry(entryID: String, entry: MusicEntry) {
        musicEntries[entryID] = entry
        saveMusicFile()
    }

    fun hasEntry(entry: MusicEntry): Boolean {
        return musicEntries.containsKey(entry.id)
    }
    fun declareError(entry: MusicEntry) {
        if (!hasEntry(entry)) return

        musicEntries[entry.id]?.error = true
        saveMusicFile()
    }

    fun saveMusicFile() {
        val jsonSettings = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        createNecessaryFiles()
        val musicJsonString = jsonSettings.encodeToString<MusicEntryList>(musicEntries)
        musicJson.writeText(musicJsonString)
    }

    fun getMediaDuration(file: File): Long {
        return getMediaMetadata(file, MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    }

    fun getMediaMetadata(file: File, field: Int): String? {
        if (!file.exists()) {
            Log.e(TAG, "File not found: ${file.absolutePath}")
            return null
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(field)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }

    fun loadUnsavedSongs(activity: FragmentActivity?) {
        if (activity == null) return
        val musicExtensions = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac")
        val filter = FileFilter { file ->
            musicExtensions.contains(file.extension)
        }

        musicPath.listFiles(filter)?.forEach { file ->
            val contains = musicEntries.values.any { entry ->
                entry.filepath == file.name
            }
            // Skip already added songs
            if (contains) {
                return@forEach
            }
            val infoEntry = getMediaMetadataAsEntry(file)
            if (infoEntry == null) {
                return@forEach
            }
            val outEntry = entryFromEntry(infoEntry, id = UUID.randomUUID().toString(), filepath = file.name)
            Log.i(TAG, outEntry.toString())
            addMusicEntry(outEntry)
            val musicPage = activity.supportFragmentManager.findFragmentByTag("MusicPage") as MusicPage
            musicPage.addMusic(-1, outEntry)
        }
    }

    fun removeAllSongs(activity: FragmentActivity?) {
        if (activity == null) return
        val musicPage = activity.supportFragmentManager.findFragmentByTag("MusicPage") as MusicPage
        musicEntries.clear()
        musicPage.removeAllSongs()

        saveMusicFile()
    }

    fun getMediaMetadataAsEntry(file: File): MusicEntry? {
        if (!file.exists()) {
            Log.e(TAG, "File not found: ${file.absolutePath}")
            return null
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            MusicEntry(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "",
                author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "",
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "",
                durationMsec = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }

    fun getMusicFileURI(context: Context) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "CuteKitten001")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/NachoMusic")
        }

        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
    }
}