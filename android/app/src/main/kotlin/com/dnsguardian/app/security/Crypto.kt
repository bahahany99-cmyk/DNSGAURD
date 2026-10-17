package com.dnsguardian.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password hashing + secure storage.
 *
 * Scheme: PBKDF2WithHmacSHA256, 128-bit salt, 240,000 iterations (OWASP-aligned for 2024+).
 * The salt + hash are stored in EncryptedSharedPreferences (AES256_GCM, AndroidKeyStore-backed).
 *
 * Argon2id would be preferable but is not in the standard Android runtime without a native lib;
 * PBKDF2 with high iteration count is a well-established, supported alternative.
 */
object Crypto {
    private const val ITERATIONS = 240_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    private fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context.applicationContext,
            com.dnsguardian.app.Constants.PREF_NAME,
            masterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATIONS,
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    /** Store a new password (enrollment). Overwrites any existing. */
    fun setPassword(context: Context, password: String) {
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        val p = prefs(context)
        p.edit()
            .putString(Constants.PREF_PASSWORD_SALT, salt.toHex())
            .putString(Constants.PREF_PASSWORD_HASH, hash.toHex())
            .putBoolean(Constants.PREF_HAS_PASSWORD, true)
            .apply()
    }

    /** Development-only: set a temporary password without strong checks. */
    fun devSetPassword(context: Context, password: String) {
        setPassword(context, password)
    }

    fun hasPassword(context: Context): Boolean =
        prefs(context).getBoolean(Constants.PREF_HAS_PASSWORD, false)

    fun verifyPassword(context: Context, password: String): Boolean {
        val p = prefs(context)
        val saltHex = p.getString(Constants.PREF_PASSWORD_SALT, null) ?: return false
        val hashHex = p.getString(Constants.PREF_PASSWORD_HASH, null) ?: return false
        val salt = saltHex.hexToBytes()
        val candidate = hashPassword(password, salt).toHex()
        // Constant-time comparison to avoid timing leaks.
        return constantTimeEquals(candidate, hashHex)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
