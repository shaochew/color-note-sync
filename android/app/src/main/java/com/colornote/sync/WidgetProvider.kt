package com.colornote.sync

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import com.colornote.sync.data.AppDatabase
import kotlinx.coroutines.runBlocking

class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val noteId = getWidgetNoteId(context, appWidgetId)
            if (noteId != null) {
                updateWidget(context, appWidgetManager, appWidgetId, noteId)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove(prefKey(appWidgetId))
        }
        editor.apply()
    }

    companion object {
        const val PREFS_NAME = "widget_prefs"
        private const val KEY_PREFIX = "widget_note_id_"
        const val ACTION_WIDGET_REFRESH = "com.colornote.sync.WIDGET_REFRESH"

        private fun prefKey(appWidgetId: Int) = "$KEY_PREFIX$appWidgetId"

        fun saveWidgetNoteId(context: Context, appWidgetId: Int, noteId: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(prefKey(appWidgetId), noteId)
                .apply()
        }

        fun getWidgetNoteId(context: Context, appWidgetId: Int): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(prefKey(appWidgetId), null)
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            noteId: String
        ) {
            val db = AppDatabase.getDatabase(context)
            val noteWithItems = runBlocking {
                db.noteDao().getNoteById(noteId)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_note)

            // Set title
            val title = noteWithItems?.note?.title ?: "Note"
            views.setTextViewText(R.id.widgetTitle, title)

            // Set background color tint
            val colorStr = noteWithItems?.note?.color ?: "#FFEB3B"
            try {
                val color = Color.parseColor(colorStr)
                // Lighten the color for the background
                val lightColor = lightenColor(color, 0.85f)
                views.setInt(R.id.widgetRoot, "setBackgroundColor", lightColor)
            } catch (e: Exception) {
                // Fallback to default cream color
            }

            // Set up the ListView adapter via RemoteViewsService
            val serviceIntent = Intent(context, WidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra("note_id", noteId)
                // Unique data URI so the system doesn't reuse the same intent for different widgets
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widgetListView, serviceIntent)

            // Set empty view (not needed, list just stays empty)

            // Click on the widget title bar opens the note
            val detailIntent = Intent(context, NoteDetailActivity::class.java).apply {
                putExtra("note_id", noteId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                detailIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetTitleBar, pendingIntent)

            // Set up pending intent template for list item clicks (also open the note)
            val itemClickIntent = Intent(context, NoteDetailActivity::class.java).apply {
                putExtra("note_id", noteId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val itemPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 1000,
                itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.widgetListView, itemPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetListView)
        }

        /**
         * Refresh all widgets that are bound to the given note.
         */
        fun refreshWidgetsForNote(context: Context, noteId: String) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (widgetId in widgetIds) {
                if (getWidgetNoteId(context, widgetId) == noteId) {
                    updateWidget(context, appWidgetManager, widgetId, noteId)
                }
            }
        }

        /**
         * Refresh all widgets.
         */
        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (widgetId in widgetIds) {
                val noteId = getWidgetNoteId(context, widgetId)
                if (noteId != null) {
                    updateWidget(context, appWidgetManager, widgetId, noteId)
                }
            }
        }

        private fun lightenColor(color: Int, factor: Float): Int {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val lr = (r + (255 - r) * factor).toInt().coerceIn(0, 255)
            val lg = (g + (255 - g) * factor).toInt().coerceIn(0, 255)
            val lb = (b + (255 - b) * factor).toInt().coerceIn(0, 255)
            return Color.argb(230, lr, lg, lb) // slightly transparent
        }
    }
}
