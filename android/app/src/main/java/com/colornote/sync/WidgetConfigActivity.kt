package com.colornote.sync

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.colornote.sync.data.AppDatabase
import com.colornote.sync.data.NoteWithItems
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var notes: List<NoteWithItems> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED in case the user backs out
        setResult(RESULT_CANCELED)

        // Get the widget ID from the intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        Log.d("ColorNoteSync", "WidgetConfigActivity: appWidgetId=$appWidgetId")

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e("ColorNoteSync", "WidgetConfigActivity: INVALID widget ID, finishing")
            finish()
            return
        }

        setContentView(R.layout.activity_widget_config)

        // Load notes from database
        val db = AppDatabase.getDatabase(this)
        notes = runBlocking {
            db.noteDao().getAllNotes().first()
        }

        Log.d("ColorNoteSync", "WidgetConfigActivity: found ${notes.size} notes")

        if (notes.isEmpty()) {
            Toast.makeText(this, "No notes yet. Create a note first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val listView = findViewById<ListView>(R.id.noteListView)
        listView.adapter = NoteListAdapter()
        listView.setOnItemClickListener { _, _, position, _ ->
            onNoteSelected(notes[position])
        }
    }

    private fun onNoteSelected(noteWithItems: NoteWithItems) {
        val noteId = noteWithItems.note.id

        // Save the selected note ID for this widget
        WidgetProvider.saveWidgetNoteId(this, appWidgetId, noteId)

        // Update the widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        WidgetProvider.updateWidget(this, appWidgetManager, appWidgetId, noteId)

        // Return success
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }

    private inner class NoteListAdapter : BaseAdapter() {

        override fun getCount(): Int = notes.size

        override fun getItem(position: Int): NoteWithItems = notes[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@WidgetConfigActivity)
                .inflate(R.layout.item_widget_config_note, parent, false)

            val noteWithItems = notes[position]
            val note = noteWithItems.note

            // Set color indicator
            val colorIndicator = view.findViewById<View>(R.id.colorIndicator)
            val drawable = colorIndicator.background
            if (drawable is GradientDrawable) {
                try {
                    drawable.setColor(Color.parseColor(note.color))
                } catch (e: Exception) {
                    drawable.setColor(Color.parseColor("#FFEB3B"))
                }
            } else {
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    try {
                        setColor(Color.parseColor(note.color))
                    } catch (e: Exception) {
                        setColor(Color.parseColor("#FFEB3B"))
                    }
                }
                colorIndicator.background = shape
            }

            // Set title
            val titleView = view.findViewById<TextView>(R.id.noteTitle)
            titleView.text = note.title.ifEmpty { "Untitled" }

            // Set item count
            val countView = view.findViewById<TextView>(R.id.noteItemCount)
            val itemCount = noteWithItems.items.size
            val doneCount = noteWithItems.items.count { it.isDone }
            countView.text = "$doneCount/$itemCount"

            return view
        }
    }
}
