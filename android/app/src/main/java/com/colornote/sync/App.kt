package com.colornote.sync

import android.app.Application
import com.colornote.sync.data.AppDatabase

class App : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        AutoSyncManager.init(this, database.noteDao())
    }
}
