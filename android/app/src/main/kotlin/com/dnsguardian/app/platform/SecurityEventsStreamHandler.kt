package com.dnsguardian.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dnsguardian.app.services.ProtectionEventBroadcaster
import io.flutter.plugin.common.EventChannel

/**
 * Streams a tick to Flutter on every state change broadcast so the UI can refresh.
 * We intentionally do NOT stream raw event data here (privacy); the UI pulls the
 * full state via the SecurityState channel after receiving the tick.
 */
class SecurityEventsStreamHandler(private val context: Context) :
    EventChannel.StreamHandler {

    private var sink: EventChannel.EventSink? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            sink?.success(System.currentTimeMillis())
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        sink = events
        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(ProtectionEventBroadcaster.ACTION_STATE_CHANGED)
        )
    }

    override fun onCancel(arguments: Any?) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        sink = null
    }
}
