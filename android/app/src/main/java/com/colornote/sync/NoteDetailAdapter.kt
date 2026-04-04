package com.colornote.sync

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.colornote.sync.data.NoteItemEntity
import java.util.Collections

class NoteDetailAdapter(
    private var isEditMode: Boolean,
    private val onToggleDone: (NoteItemEntity) -> Unit,
    private val onDeleteItem: (NoteItemEntity) -> Unit,
    private val onItemClick: (NoteItemEntity) -> Unit,
    private val onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_VIEW = 0
        private const val VIEW_TYPE_EDIT = 1
    }

    private val items = mutableListOf<NoteItemEntity>()

    fun getItems(): List<NoteItemEntity> = items.toList()

    fun setItems(newItems: List<NoteItemEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < items.size && toPosition < items.size) {
            Collections.swap(items, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isEditMode) VIEW_TYPE_EDIT else VIEW_TYPE_VIEW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_EDIT) {
            val view = inflater.inflate(R.layout.item_note_edit, parent, false)
            EditViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_note_view, parent, false)
            ViewViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is ViewViewHolder -> holder.bind(item)
            is EditViewHolder -> holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textContent: TextView = itemView.findViewById(R.id.textItemContent)
        private val iconCheckmark: ImageView = itemView.findViewById(R.id.iconCheckmark)
        private val btnItemMenu: ImageButton = itemView.findViewById(R.id.btnItemMenu)

        fun bind(item: NoteItemEntity) {
            textContent.text = item.text

            if (item.isDone) {
                textContent.paintFlags = textContent.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                textContent.setTextColor(Color.GRAY)
                iconCheckmark.visibility = View.VISIBLE
                btnItemMenu.visibility = View.GONE
            } else {
                textContent.paintFlags = textContent.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                textContent.setTextColor(Color.BLACK)
                iconCheckmark.visibility = View.GONE
                btnItemMenu.visibility = View.VISIBLE
            }

            itemView.setOnClickListener {
                onToggleDone(item)
            }

            btnItemMenu.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    inner class EditViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textContent: TextView = itemView.findViewById(R.id.textItemContent)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        val dragHandle: ImageView = itemView.findViewById(R.id.iconDragHandle)

        @Suppress("ClickableViewAccessibility")
        fun bind(item: NoteItemEntity) {
            textContent.text = item.text

            if (item.isDone) {
                textContent.paintFlags = textContent.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                textContent.setTextColor(Color.GRAY)
            } else {
                textContent.paintFlags = textContent.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                textContent.setTextColor(Color.BLACK)
            }

            btnDelete.setOnClickListener {
                onDeleteItem(item)
            }

            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag?.invoke(this)
                }
                false
            }
        }
    }
}
