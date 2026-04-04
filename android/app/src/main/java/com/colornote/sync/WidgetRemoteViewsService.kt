package com.colornote.sync

import android.content.Intent
import android.widget.RemoteViewsService

class WidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetRemoteViewsFactory(applicationContext, intent)
    }
}
