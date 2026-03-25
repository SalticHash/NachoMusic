package com.saltichash.musicapp

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast


class SettingsPage : Fragment() {
    private lateinit var btnSeeSongs: Button
    private lateinit var btnCheckSongs: Button
    private lateinit var btnDeleteAllSongs: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings_page, container, false)

        btnSeeSongs = view.findViewById(R.id.btnSeeSongs)
        btnCheckSongs = view.findViewById(R.id.btnCheckSongs)
        btnDeleteAllSongs = view.findViewById(R.id.btnDeleteAllSongs)

        btnSeeSongs.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(
                    "android.provider.extra.INITIAL_URI",
                    Uri.parse("content://com.android.externalstorage.documents/document/primary:Android%2Fmedia%2F${context?.packageName}")
                )
            }
            startActivity(intent)

        }
        btnCheckSongs.setOnClickListener {


            if (!MusicManager.musicPath.exists()) {
                Toast.makeText(requireContext(), R.string.unknown_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            MusicManager.loadUnsavedSongs(activity)
        }

        btnDeleteAllSongs.setOnClickListener {
            val builder = AlertDialog.Builder(context)
                .setTitle(R.string.delete_all_title)
                .setMessage(R.string.delete_all_msg)
                .setNegativeButton(R.string.cancel) { dialog, which -> return@setNegativeButton}

                .setPositiveButton(R.string.yes) { dialog, which ->
                    MusicManager.removeAllSongs(activity)
                }

            builder.create().show()
        }
        return view
    }
}