package com.jzb.tower.android.platform

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Placeholder foreground service; backend agent (`gpt-6.2-sol`) implements full
 * notification channel, persistent LAN sharing status notification, and Stop action.
 */
class LanSharingForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.jzb.tower.action.LAN_START"
        const val ACTION_STOP = "com.jzb.tower.action.LAN_STOP"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_PORT = "extra_port"
    }
}
