package com.dnsguardian.app.services

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Helper to push state-change notifications to the Flutter EventChannel.
 * Uses LocalBroadcastManager (no IPC, privacy-safe) so the MainActivity's
 * SecurityEventsStreamHandler can refresh the UI.
 */
object ProtectionEventBroadcaster {
    const val ACTION_STATE_CHANGED = "com.dnsguardian.app.STATE_CHANGED"

    fun notifyStateChanged(context: Context) {
        val intent = Intent(ACTION_STATE_CHANGED)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}
