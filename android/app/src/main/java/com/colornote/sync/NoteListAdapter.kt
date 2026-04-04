package com.colornote.sync

import android.graphics.Color
import android.graphics.Paint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.colornote.sync.data.NoteWithItems
import com.google.android.material.card.MaterialCardView

class NoteListAdapter(
    private val onNoteClick: (String) -> Unit
) : ListAdapter<NoteWithItems, NoteListAdapter.NoteViewHolder>(NoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note_card, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val cardNote: MaterialCardView = itemView.findViewById(R.id.cardNote)
        private val tvNoteTitle: TextView = itemView.findViewById(R.id.tvNoteTitle)
        private val tvItem1: TextView = itemView.findViewById(R.id.tvItem1)
        private val tvItem2: TextView = itemView.findViewById(R.id.tvItem2)
        private val tvItem3: TextView = itemView.findViewById(R.id.tvItem3)
        private val tvMeta: TextView = itemView.findViewById(R.id.tvMeta)

        fun bind(noteWithItems: NoteWithItems) {
            val note = noteWithItems.note
            val items = noteWithItems.items.sortedBy { it.sortOrder }

            // Set card background color
            try {
                cardNote.setCardBackgroundColor(Color.parseColor(note.color))
            } catch (_: Exception) {
                cardNote.setCardBackgroundColor(Color.parseColor("#FFEB3B"))
            }

            // Title
            tvNoteTitle.text = note.title.ifEmpty { "Untitled" }

            // Preview items
            val previewViews = listOf(tvItem1, tvItem2, tvItem3)
            previewViews.forEach { it.visibility = View.GONE }

            items.take(3).forEachIndexed { index, item ->
                val tv = previewViews[index]
                tv.visibility = View.VISIBLE
                tv.text = item.text.ifEmpty { " " }

                if (item.isDone) {
                    tv.paintFlags = tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    tv.setTextColor(Color.parseColor("#999999"))
                } else {
                    tv.paintFlags = tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    tv.setTextColor(Color.parseColor("#333333"))
                }
            }

            // Meta: item count + relative time
            val count = items.size
            val itemText = if (count == 1) "1 item" else "$count items"
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                note.updatedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            tvMeta.text = "$itemText \u00B7 $relativeTime"

            // Click listener
            cardNote.setOnClickListener {
                onNoteClick(note.id)
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<NoteWithItems>() {
        override fun areItemsTheSame(oldItem: NoteWithItems, newItem: NoteWithItems): Boolean {
            return oldItem.note.id == newItem.note.id
        }

        override fun areContentsTheSame(oldItem: NoteWithItems, newItem: NoteWithItems): Boolean {
            return oldItem.note == newItem.note && oldItem.items == newItem.items
        }
    }
}
