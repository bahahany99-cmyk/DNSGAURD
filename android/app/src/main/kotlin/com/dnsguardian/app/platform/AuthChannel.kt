package com.dnsguardian.app.platform

import android.content.Context
import com.dnsguardian.app.security.Crypto
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * Password enrollment + verification.
 *
 * - hasPassword: whether a password is already set.
 * - devSetPassword: TEMPORARY dev/test password (MUST be stripped from release builds).
 * - enrollPassword: final owner password enrollment (Phase 9).
 * - verifyPassword: verify against stored hash.
 *
 * Passwords are never stored as plaintext; only PBKDF2 hash + salt in EncryptedSharedPreferences.
 */
class AuthChannel(private val context: Context) : MethodChannel.MethodCallHandler {

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "hasPassword" -> {
                result.success(Crypto.hasPassword(context))
            }
            "devSetPassword" -> {
                val pw = call.argument<String>("password") ?: run {
                    result.error("BAD_ARGS", "password required", null); return
                }
                if (!BuildConfig.DEBUG) {
                    // Strip dev path from release builds.
                    result.error("FORBIDDEN", "devSetPassword not allowed in release", null)
                    return
                }
                Crypto.devSetPassword(context, pw)
                result.success(true)
            }
            "enrollPassword" -> {
                val pw = call.argument<String>("password") ?: run {
                    result.error("BAD_ARGS", "password required", null); return
                }
                // Enforce minimum strength policy (>=12 chars, mixed classes handled in UI too).
                if (pw.length < 12) {
                    result.error("WEAK", "Password too short (min 12)", null)
                    return
                }
                Crypto.setPassword(context, pw)
                result.success(true)
            }
            "verifyPassword" -> {
                val pw = call.argument<String>("password") ?: run {
                    result.error("BAD_ARGS", "password required", null); return
                }
                result.success(Crypto.verifyPassword(context, pw))
            }
            else -> result.notImplemented()
        }
    }
}
