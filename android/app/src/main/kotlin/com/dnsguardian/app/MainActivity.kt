package com.dnsguardian.app

import android.os.Bundle
import com.dnsguardian.app.platform.ActionChannel
import com.dnsguardian.app.platform.AuthChannel
import com.dnsguardian.app.platform.SecurityEventsStreamHandler
import com.dnsguardian.app.platform.SecurityStateChannel
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val binaryMessenger = flutterEngine.dartExecutor.binaryMessenger

        // Security state channel
        io.flutter.plugin.common.MethodChannel(
            binaryMessenger, Constants.CHANNEL_STATE
        ).setMethodCallHandler(SecurityStateChannel(this))

        // Auth channel
        io.flutter.plugin.common.MethodChannel(
            binaryMessenger, Constants.CHANNEL_AUTH
        ).setMethodCallHandler(AuthChannel(this))

        // Action channel
        io.flutter.plugin.common.MethodChannel(
            binaryMessenger, Constants.CHANNEL_ACTION
        ).setMethodCallHandler(ActionChannel(this))

        // Event channel (state-change ticks)
        io.flutter.plugin.common.EventChannel(
            binaryMessenger, Constants.CHANNEL_EVENTS
        ).setStreamHandler(SecurityEventsStreamHandler(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
