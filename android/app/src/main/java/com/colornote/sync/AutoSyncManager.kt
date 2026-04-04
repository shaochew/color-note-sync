package com.colornote.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.colornote.sync.api.RetrofitClient
import com.colornote.sync.data.NoteDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AutoSyncManager : DefaultLifecycleObserver {

    private const val TAG = "AutoSync"
    private const val DEBOUNCE_MS = 3000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null
    private var isForeground = false
    private var dao: NoteDao? = null
    private var appContext: Context? = null

    fun init(context: Context, noteDao: NoteDao) {
        appContext = context.applicationContext
        dao = noteDao
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        // App came to foreground
        isForeground = true
        Log.d(TAG, "App in foreground — triggering sync")
        syncNow()
    }

    override fun onStop(owner: LifecycleOwner) {
        // App went to background
        isForeground = false
        debounceJob?.cancel()
        Log.d(TAG, "App in background — sync disabled")
    }

    /**
     * Call after any local data change. Debounces to avoid rapid-fire syncs.
     */
    fun notifyDataChanged() {
        if (!isForeground) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            performSync()
        }
    }

    /**
     * Sync immediately (no debounce). Used on app open.
     */
    fun syncNow() {
        if (!isForeground) return
        debounceJob?.cancel()
        scope.launch {
            performSync()
        }
    }

    private suspend fun performSync() {
        val context = appContext ?: return
        val noteDao = dao ?: return

        try {
            val apiService = RetrofitClient.getService(context)
            val syncManager = SyncManager(noteDao, apiService)
            val result = syncManager.sync()
            Log.d(TAG, "Auto-sync result: success=${result.success} message=${result.message}")
            if (result.success) {
                WidgetProvider.refreshAllWidgets(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-sync failed: ${e.message}")
        }
    }
}
