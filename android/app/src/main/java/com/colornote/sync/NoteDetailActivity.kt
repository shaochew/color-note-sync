package com.colornote.sync

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.colornote.sync.data.NoteDao
import com.colornote.sync.data.NoteEntity
import com.colornote.sync.data.NoteItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class NoteDetailActivity : AppCompatActivity() {

    private lateinit var dao: NoteDao
    private lateinit var adapter: NoteDetailAdapter
    private var noteId: String = ""
    private var currentNote: NoteEntity? = null
    private var isEditMode = false
    private var currentColor: String = "#FFEB3B"

    // Undo/redo stacks
    private val undoStack = mutableListOf<List<NoteItemEntity>>()
    private val redoStack = mutableListOf<List<NoteItemEntity>>()

    // Views
    private lateinit var rootLayout: LinearLayout
    private lateinit var textTitle: TextView
    private lateinit var editTitle: EditText
    private lateinit var colorPickerSquare: View
    private lateinit var btnEditSave: ImageButton
    private lateinit var btnOverflow: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var textTimestamp: TextView
    private lateinit var textEditingLabel: TextView
    private lateinit var recyclerItems: RecyclerView
    private lateinit var addItemBar: LinearLayout
    private lateinit var btnAddItem: TextView
    private lateinit var btnColorPicker: ImageButton
    private lateinit var bottomBar: LinearLayout
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton

    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_detail)

        dao = (application as App).database.noteDao()
        noteId = intent.getStringExtra("note_id") ?: run {
            finish()
            return
        }

        bindViews()
        setupRecyclerView()
        setupClickListeners()
        loadNote()
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        textTitle = findViewById(R.id.textTitle)
        editTitle = findViewById(R.id.editTitle)
        colorPickerSquare = findViewById(R.id.colorPickerSquare)
        btnEditSave = findViewById(R.id.btnEditSave)
        btnOverflow = findViewById(R.id.btnOverflow)
        btnBack = findViewById(R.id.btnBack)
        textTimestamp = findViewById(R.id.textTimestamp)
        textEditingLabel = findViewById(R.id.textEditingLabel)
        recyclerItems = findViewById(R.id.recyclerItems)
        addItemBar = findViewById(R.id.addItemBar)
        btnAddItem = findViewById(R.id.btnAddItem)
        btnColorPicker = findViewById(R.id.btnColorPicker)
        bottomBar = findViewById(R.id.bottomBar)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
    }

    private fun setupRecyclerView() {
        adapter = NoteDetailAdapter(
            isEditMode = false,
            onToggleDone = { item -> toggleItemDone(item) },
            onDeleteItem = { item -> deleteItem(item) },
            onItemClick = { /* item menu click in view mode - no-op for now */ },
            onStartDrag = { viewHolder -> itemTouchHelper?.startDrag(viewHolder) },
            onAddItem = { showAddItemDialog() },
            onEditItem = { item -> showEditItemDialog(item) }
        )

        recyclerItems.layoutManager = LinearLayoutManager(this)
        recyclerItems.adapter = adapter
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            if (isEditMode) {
                saveAndExitEditMode()
            } else {
                finish()
            }
        }

        btnEditSave.setOnClickListener {
            if (isEditMode) {
                saveAndExitEditMode()
            } else {
                enterEditMode()
            }
        }

        btnOverflow.setOnClickListener { view ->
            showOverflowMenu(view)
        }

        addItemBar.setOnClickListener {
            showAddItemDialog()
        }

        btnAddItem.setOnClickListener {
            showAddItemDialog()
        }

        colorPickerSquare.setOnClickListener {
            showColorPickerDialog()
        }

        btnColorPicker.setOnClickListener {
            showColorPickerDialog()
        }

        btnUndo.setOnClickListener { performUndo() }
        btnRedo.setOnClickListener { performRedo() }
    }

    private fun loadNote() {
        lifecycleScope.launch {
            val noteWithItems = withContext(Dispatchers.IO) {
                dao.getNoteById(noteId)
            }

            if (noteWithItems == null) {
                finish()
                return@launch
            }

            currentNote = noteWithItems.note
            currentColor = noteWithItems.note.color

            applyNoteColor(currentColor)
            textTitle.text = noteWithItems.note.title
            editTitle.setText(noteWithItems.note.title)

            val relativeTime = DateUtils.getRelativeTimeSpanString(
                noteWithItems.note.updatedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            textTimestamp.text = relativeTime

            val sortedItems = noteWithItems.items.sortedBy { it.sortOrder }
            adapter.setItems(sortedItems)

            // Initialize undo stack with current state
            undoStack.clear()
            redoStack.clear()
            undoStack.add(sortedItems.toList())
        }
    }

    private fun applyNoteColor(colorHex: String) {
        try {
            val color = Color.parseColor(colorHex)
            rootLayout.setBackgroundColor(color)
            colorPickerSquare.setBackgroundColor(color)
        } catch (_: IllegalArgumentException) {
            // Ignore invalid color
        }
    }

    private fun enterEditMode() {
        isEditMode = true

        textTitle.visibility = View.GONE
        editTitle.visibility = View.VISIBLE
        editTitle.setText(textTitle.text)

        colorPickerSquare.visibility = View.VISIBLE
        btnColorPicker.visibility = View.VISIBLE
        textEditingLabel.visibility = View.VISIBLE
        addItemBar.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE

        btnEditSave.setImageResource(R.drawable.ic_check)
        btnEditSave.contentDescription = "Save"

        adapter.setEditMode(true)
        attachItemTouchHelper()
        updateUndoRedoButtons()
    }

    private fun saveAndExitEditMode() {
        isEditMode = false

        val newTitle = editTitle.text.toString().trim()

        textTitle.text = newTitle
        textTitle.visibility = View.VISIBLE
        editTitle.visibility = View.GONE

        colorPickerSquare.visibility = View.GONE
        btnColorPicker.visibility = View.GONE
        textEditingLabel.visibility = View.GONE
        addItemBar.visibility = View.GONE
        bottomBar.visibility = View.GONE

        btnEditSave.setImageResource(R.drawable.ic_edit)
        btnEditSave.contentDescription = "Edit"

        adapter.setEditMode(false)
        detachItemTouchHelper()

        // Persist title and color changes
        lifecycleScope.launch {
            currentNote?.let { note ->
                val updatedNote = note.copy(
                    title = newTitle,
                    color = currentColor,
                    updatedAt = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    dao.updateNote(updatedNote)
                }
                currentNote = updatedNote
                applyNoteColor(currentColor)

                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    updatedNote.updatedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
                textTimestamp.text = relativeTime
                AutoSyncManager.notifyDataChanged()
            }
        }
    }

    private fun toggleItemDone(item: NoteItemEntity) {
        lifecycleScope.launch {
            val updatedItem = item.copy(isDone = !item.isDone)
            withContext(Dispatchers.IO) {
                dao.updateItem(updatedItem)
            }
            refreshItems()
        }
    }

    private fun deleteItem(item: NoteItemEntity) {
        pushUndoState()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteItem(item)
            }
            refreshItems()
        }
    }

    private fun refreshItems() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                dao.getItemsByNoteId(noteId)
            }
            val sorted = items.sortedBy { it.sortOrder }
            adapter.setItems(sorted)
            WidgetProvider.refreshAllWidgets(this@NoteDetailActivity)
            AutoSyncManager.notifyDataChanged()
        }
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Delete Note")
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    showDeleteConfirmation()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    currentNote?.let { note ->
                        withContext(Dispatchers.IO) {
                            dao.deleteNote(note)
                        }
                    }
                    AutoSyncManager.notifyDataChanged()
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddItemDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_item, null)
        val editItemText = dialogView.findViewById<EditText>(R.id.editItemText)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("NEXT", null)
            .create()

        dialog.show()

        // Override button clicks to control dialog dismissal
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = editItemText.text.toString().trim()
            if (text.isNotEmpty()) {
                addNewItem(text)
            }
            dialog.dismiss()
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val text = editItemText.text.toString().trim()
            if (text.isNotEmpty()) {
                addNewItem(text)
                editItemText.setText("")
            }
            // Don't dismiss - keep dialog open for next item
        }
    }

    private fun showEditItemDialog(item: NoteItemEntity) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_item, null)
        val editItemText = dialogView.findViewById<EditText>(R.id.editItemText)
        editItemText.setText(item.text)
        editItemText.setSelection(item.text.length)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val newText = editItemText.text.toString().trim()
                if (newText.isNotEmpty() && newText != item.text) {
                    updateItemText(item, newText)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateItemText(item: NoteItemEntity, newText: String) {
        pushUndoState()
        lifecycleScope.launch {
            val updatedItem = item.copy(text = newText)
            withContext(Dispatchers.IO) {
                dao.updateItem(updatedItem)
            }
            refreshItems()
        }
    }

    private fun addNewItem(text: String) {
        pushUndoState()
        lifecycleScope.launch {
            val currentItems = adapter.getItems()
            val maxSortOrder = currentItems.maxOfOrNull { it.sortOrder } ?: -1

            val newItem = NoteItemEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                text = text,
                isDone = false,
                sortOrder = maxSortOrder + 1,
                createdAt = System.currentTimeMillis()
            )

            withContext(Dispatchers.IO) {
                dao.insertItem(newItem)
            }

            // Update the note's updatedAt timestamp
            currentNote?.let { note ->
                val updatedNote = note.copy(updatedAt = System.currentTimeMillis())
                withContext(Dispatchers.IO) {
                    dao.updateNote(updatedNote)
                }
                currentNote = updatedNote
            }

            refreshItems()
        }
    }

    private fun showColorPickerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_color_picker, null)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Pick a color")
            .setView(dialogView)
            .create()

        val colorViews = listOf(
            dialogView.findViewById<View>(R.id.colorYellow),
            dialogView.findViewById<View>(R.id.colorGreen),
            dialogView.findViewById<View>(R.id.colorBlue),
            dialogView.findViewById<View>(R.id.colorPink),
            dialogView.findViewById<View>(R.id.colorOrange),
            dialogView.findViewById<View>(R.id.colorWhite),
            dialogView.findViewById<View>(R.id.colorPurple),
            dialogView.findViewById<View>(R.id.colorRed)
        )

        for (colorView in colorViews) {
            colorView.setOnClickListener {
                val colorHex = it.tag as String
                currentColor = colorHex
                applyNoteColor(colorHex)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // --- Drag and drop ---

    private fun attachItemTouchHelper() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                // Don't allow moving to/from the footer position
                if (fromPos >= adapter.getItems().size || toPos >= adapter.getItems().size) return false
                adapter.moveItem(fromPos, toPos)
                return true
            }

            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                // Don't allow dropping onto the footer
                return target.adapterPosition < adapter.getItems().size
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe
            }

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // Don't allow dragging the footer
                if (viewHolder is NoteDetailAdapter.FooterViewHolder) return 0
                return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    pushUndoState()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // Persist new sort order
                persistSortOrder()
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerItems)
    }

    private fun detachItemTouchHelper() {
        itemTouchHelper?.attachToRecyclerView(null)
        itemTouchHelper = null
    }

    private fun persistSortOrder() {
        val items = adapter.getItems()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                items.forEachIndexed { index, item ->
                    val updated = item.copy(sortOrder = index)
                    dao.updateItem(updated)
                }
            }
            refreshItems()
        }
    }

    // --- Undo / Redo ---

    private fun pushUndoState() {
        undoStack.add(adapter.getItems().toList())
        redoStack.clear()
        updateUndoRedoButtons()
    }

    private fun performUndo() {
        if (undoStack.size <= 1) return // Keep at least the initial state

        val currentState = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(currentState)

        val previousState = undoStack.last()
        restoreState(previousState)
        updateUndoRedoButtons()
    }

    private fun performRedo() {
        if (redoStack.isEmpty()) return

        val nextState = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(nextState)

        restoreState(nextState)
        updateUndoRedoButtons()
    }

    private fun restoreState(items: List<NoteItemEntity>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Delete all current items and re-insert the snapshot
                dao.deleteAllItemsByNoteId(noteId)
                items.forEachIndexed { index, item ->
                    dao.insertItem(item.copy(sortOrder = index))
                }
            }
            refreshItems()
        }
    }

    private fun updateUndoRedoButtons() {
        btnUndo.isEnabled = undoStack.size > 1
        btnUndo.alpha = if (undoStack.size > 1) 1.0f else 0.3f
        btnRedo.isEnabled = redoStack.isNotEmpty()
        btnRedo.alpha = if (redoStack.isNotEmpty()) 1.0f else 0.3f
    }
}
