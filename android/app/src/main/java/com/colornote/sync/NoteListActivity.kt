package com.colornote.sync

import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.colornote.sync.api.RetrofitClient
import com.colornote.sync.data.NoteEntity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class NoteListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var fabAddNote: FloatingActionButton
    private lateinit var adapter: NoteListAdapter

    private val noteDao by lazy {
        (application as App).database.noteDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.recyclerViewNotes)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        fabAddNote = findViewById(R.id.fabAddNote)

        adapter = NoteListAdapter { noteId ->
            openNoteDetail(noteId)
        }

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter

        fabAddNote.setOnClickListener {
            showNewNoteDialog()
        }

        observeNotes()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_note_list, menu)
        Log.d("ColorNoteSync", "Menu inflated with ${menu.size()} items")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d("ColorNoteSync", "Menu item selected: ${item.title}")
        return when (item.itemId) {
            R.id.action_sync -> {
                performSync()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performSync() {
        Log.d("ColorNoteSync", "performSync() called")
        val apiService = RetrofitClient.getService(this)
        val syncManager = SyncManager(noteDao, apiService)

        Toast.makeText(this, "Syncing...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            Log.d("ColorNoteSync", "Starting sync coroutine...")
            val result = withContext(Dispatchers.IO) {
                syncManager.sync()
            }

            Log.d("ColorNoteSync", "Sync result: success=${result.success} message=${result.message}")

            if (result.success) {
                Toast.makeText(this@NoteListActivity, result.message, Toast.LENGTH_SHORT).show()
                WidgetProvider.refreshAllWidgets(this@NoteListActivity)
            } else {
                Toast.makeText(this@NoteListActivity, "Sync failed: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun observeNotes() {
        lifecycleScope.launch {
            noteDao.getAllNotes().collectLatest { notes ->
                adapter.submitList(notes)
                if (notes.isEmpty()) {
                    tvEmptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showNewNoteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_note, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etNoteTitle)
        val colorContainer = dialogView.findViewById<LinearLayout>(R.id.colorContainer)

        var selectedColor = "#FFEB3B"
        var selectedBorder: View? = null

        // Set up color selection
        for (i in 0 until colorContainer.childCount) {
            val frame = colorContainer.getChildAt(i) as FrameLayout
            val colorView = frame.getChildAt(0)
            val colorHex = colorView.tag as String

            // Highlight first color by default
            if (colorHex == selectedColor) {
                highlightColorView(colorView, true)
                selectedBorder = colorView
            }

            frame.setOnClickListener {
                // Deselect previous
                selectedBorder?.let { highlightColorView(it, false) }

                // Select new
                selectedColor = colorHex
                highlightColorView(colorView, true)
                selectedBorder = colorView
            }
        }

        AlertDialog.Builder(this)
            .setTitle("New Note")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    createNote(title, selectedColor)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun highlightColorView(view: View, selected: Boolean) {
        val bg = view.background
        if (bg is GradientDrawable) {
            if (selected) {
                bg.setStroke(6, Color.parseColor("#333333"))
            } else {
                bg.setStroke(4, Color.parseColor("#E0E0E0"))
            }
        }
    }

    private fun createNote(title: String, color: String) {
        val noteId = UUID.randomUUID().toString()
        val note = NoteEntity(
            id = noteId,
            title = title,
            color = color,
            updatedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )

        lifecycleScope.launch(Dispatchers.IO) {
            noteDao.insertNote(note)
        }
        AutoSyncManager.notifyDataChanged()

        openNoteDetail(noteId)
    }

    private fun openNoteDetail(noteId: String) {
        val intent = Intent(this, NoteDetailActivity::class.java)
        intent.putExtra("note_id", noteId)
        startActivity(intent)
    }
}
