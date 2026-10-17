package com.dnsguardian.app.services

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opaque, touch-blocking response surface shown only after the Accessibility
 * service detects a DNS-related Android Settings window. It does not read or
 * modify DNS configuration.
 */
class PrivateDnsBlockingOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val isShowing: Boolean
        get() = view != null

    fun show(onReturnToApp: () -> Unit): Boolean {
        if (view != null) return true
        if (!Settings.canDrawOverlays(context) || windowManager == null) return false

        val density = context.resources.displayMetrics.density
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((32 * density).toInt(), (48 * density).toInt(),
                (32 * density).toInt(), (48 * density).toInt())
            setBackgroundColor(Color.rgb(248, 250, 242))
            isClickable = true
            isFocusable = true
        }
        panel.addView(TextView(context).apply {
            text = "تم حظر الوصول إلى إعدادات DNS\nDNS settings access blocked"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(24, 80, 34))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panel.addView(TextView(context).apply {
            text = "ارجع إلى حارس DNS وأدخل كلمة المرور للمتابعة."
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, (24 * density).toInt(), 0, (32 * density).toInt())
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        panel.addView(Button(context).apply {
            text = "العودة إلى حارس DNS"
            textSize = 18f
            contentDescription = "العودة إلى شاشة قفل حارس DNS"
            setOnClickListener {
                hide()
                onReturnToApp()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.FILL
            title = "DNS Guardian blocking overlay"
        }

        return try {
            windowManager.addView(panel, params)
            view = panel
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    fun hide() {
        val attached = view ?: return
        view = null
        try {
            windowManager?.removeViewImmediate(attached)
        } catch (_: RuntimeException) {
            // The window may already have been removed by Android.
        }
    }
}
