package com.dnsguardian.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.dnsguardian.app.platform.ActionChannel
import com.dnsguardian.app.platform.AuthChannel
import com.dnsguardian.app.platform.SecurityEventsStreamHandler
import com.dnsguardian.app.platform.SecurityStateChannel
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var actionChannel: ActionChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Log.i(TAG, "stage=configure_flutter_engine")

        try {
            val binaryMessenger = flutterEngine.dartExecutor.binaryMessenger

            io.flutter.plugin.common.MethodChannel(
                binaryMessenger, Constants.CHANNEL_STATE
            ).setMethodCallHandler(SecurityStateChannel(this))

            io.flutter.plugin.common.MethodChannel(
                binaryMessenger, Constants.CHANNEL_AUTH
            ).setMethodCallHandler(AuthChannel(this))

            actionChannel = ActionChannel(this)
            io.flutter.plugin.common.MethodChannel(
                binaryMessenger, Constants.CHANNEL_ACTION
            ).setMethodCallHandler(actionChannel)

            io.flutter.plugin.common.EventChannel(
                binaryMessenger, Constants.CHANNEL_EVENTS
            ).setStreamHandler(SecurityEventsStreamHandler(this))
            Log.i(TAG, "stage=platform_channels_registered")
        } catch (error: Throwable) {
            // Flutter can still render its local recovery UI if native channel
            // setup fails. The Dart wrappers degrade to an honest unknown state.
            Log.e(TAG, "stage=platform_channel_registration_failed", error)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "stage=activity_onCreate")
        super.onCreate(savedInstanceState)
        Log.i(TAG, "stage=activity_created")
    }

    @Deprecated("Deprecated in Android; required by the Device Admin activation contract")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (actionChannel?.onActivityResult(requestCode) == true) return
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        actionChannel?.dispose()
        actionChannel = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DNSGuardian.Startup"
    }
}
