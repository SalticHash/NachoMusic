package com.saltichash.musicapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MusicEntryAdapter(private val dataSet: MutableList<MusicEntry>,
                        private val onClickListener: OnClickListener,
                        private val onLongClickListener: OnLongClickListener) :
    RecyclerView.Adapter<MusicEntryAdapter.ViewHolder>() {
    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Define click listener for the ViewHolder's View
        val container: ConstraintLayout = view.findViewById(R.id.main)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDesc: TextView = view.findViewById(R.id.tvDesc)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val icError: ImageView = view.findViewById(R.id.icError)
    }

    override fun getItemId(position: Int): Long {
        return filteredList[position].id.hashCode().toLong()
    }

    init {
        setHasStableIds(true)
    }


    private var filteredList: MutableList<MusicEntry> = dataSet.toMutableList()
    fun getPosition(item: MusicEntry): Int {
        return dataSet.indexOf(item)
    }

    fun updateList(newList: List<MusicEntry>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = filteredList.size
            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return filteredList[oldItemPosition].id == newList[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return filteredList[oldItemPosition] == newList[newItemPosition]
            }
        })

        filteredList.clear()
        filteredList.addAll(newList)

        diff.dispatchUpdatesTo(this)
    }

    private var lastQuery: String? = ""
    fun filter(query: String?) {
        lastQuery = query

        // "Ricardo, Minutes   , Wow ows" -> ["Ricardo", "Minutes", "Wow ows"]
        val searchQuotes: List<String> =
            (query ?: "").split(Regex("[.,;|]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val newList = if (query.isNullOrBlank()) {
            dataSet.toMutableList()
        } else {
            dataSet.filter {
                searchQuotes.any { q ->
                    it.title.contains(q, true) ||
                    it.author.contains(q, true) ||
                    it.album.contains(q, true) ||
                    it.tags.any { tag -> tag.contains(q, true) }
                }
            }.toMutableList()
        }


        updateList(newList)
    }

    // Returns idx of added entry
    fun addNewEntry(entry: MusicEntry) {
        dataSet.add(entry)
        filter(lastQuery)
    }

    fun removeEntry(position: Int) {
        dataSet.removeAt(position)
        filter(lastQuery)
    }

    fun removeAllEntries() {
        dataSet.clear()
        filter(lastQuery)
    }


    fun editEntry(position: Int, entry: MusicEntry) {
        dataSet[position] = entry
        notifyItemChanged(filteredList.indexOf(entry), entry)
        filter(lastQuery)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_music_entry, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        val entry: MusicEntry = filteredList[position]

        viewHolder.tvTitle.text = if (entry.title.isBlank()) {
            viewHolder.itemView.context.getString(R.string.unnamed_song)
        } else {entry.title}


        val tags = entry.tags
            .map { it.lowercase().replaceFirstChar { c -> c.uppercaseChar() } }
            .joinToString(", ")


        val author = if (entry.author.isBlank()) {
            viewHolder.itemView.context.getString(R.string.unauthored_song)
        } else {entry.author}

        // Possible outcomes:
        // (Maria - Album) / (Author - Album)
        // (Maria)
        // ""
        val creditsText = if (entry.album.isNotBlank()) {
            String.format(Locale.getDefault(), "(%s - %s)", author, entry.album)
        } else if (entry.author.isNotBlank()) {
            String.format(Locale.getDefault(), "(%s)", entry.author)
        } else {""}

        viewHolder.tvDesc.text = if (creditsText.isNotBlank()) {
            if (tags.isNotBlank()) {
                String.format(Locale.getDefault(), "%s • %s", creditsText, tags)
            } else {
                String.format(Locale.getDefault(), "%s", creditsText)
            }
        } else {
            String.format(Locale.getDefault(), "%s", tags)
        }


        if (entry.error) {
            viewHolder.icError.visibility = View.VISIBLE
        }

        if (entry.author.isBlank() && entry.album.isBlank() && tags.isBlank()) {
            viewHolder.tvDesc.visibility = View.GONE
        } else {
            viewHolder.tvDesc.visibility = View.VISIBLE
        }

        val secondsTotal = entry.durationMsec / 1000
        val minutes = secondsTotal / 60
        val seconds = secondsTotal % 60
        viewHolder.tvDuration.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

        viewHolder.container.setOnClickListener {
            onClickListener.onClick(position, entry)
        }
        viewHolder.container.setOnLongClickListener {
            onLongClickListener.onLongClick(position, entry)
        }
    }

    // Interface for the click listener
    interface OnClickListener {
        fun onClick(position: Int, model: MusicEntry)
    }

    // Interface for the double-click listener
    interface OnLongClickListener {
        fun onLongClick(position: Int, model: MusicEntry): Boolean
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = filteredList.size
    fun getRealItemCount() = dataSet.size
}