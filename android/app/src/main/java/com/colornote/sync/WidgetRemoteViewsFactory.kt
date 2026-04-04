package com.colornote.sync

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.colornote.sync.data.AppDatabase
import com.colornote.sync.data.NoteItemEntity
import kotlinx.coroutines.runBlocking

class WidgetRemoteViewsFactory(
    private val context: Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<NoteItemEntity> = emptyList()
    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private val noteId: String? = intent.getStringExtra("note_id")

    override fun onCreate() {
        // Initial data load happens in onDataSetChanged
    }

    override fun onDataSetChanged() {
        if (noteId == null) return
        val db = AppDatabase.getDatabase(context)
        // This runs on a binder thread, so blocking is fine
        items = runBlocking {
            db.noteDao().getItemsByNoteId(noteId)
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= items.size) {
            return RemoteViews(context.packageName, R.layout.widget_note_item_normal)
        }

        val item = items[position]
        val layoutId = if (item.isDone) {
            R.layout.widget_note_item_done
        } else {
            R.layout.widget_note_item_normal
        }

        val views = RemoteViews(context.packageName, layoutId)
        views.setTextViewText(R.id.widgetItemText, item.text)

        // Apply strikethrough for done items via paint flags
        if (item.isDone) {
            views.setInt(
                R.id.widgetItemText,
                "setPaintFlags",
                Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
            )
        }

        // Set fill-in intent for item click
        val fillInIntent = Intent()
        views.setOnClickFillInIntent(R.id.widgetItemText, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2 // normal + done

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false
}
