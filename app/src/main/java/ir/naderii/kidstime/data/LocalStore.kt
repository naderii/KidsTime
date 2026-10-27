package ir.naderii.kidstime.data

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local-only prototype storage.
 * Production version should migrate structured data to Room/DataStore and keep
 * the PIN verifier protected with Android Keystore.
 */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("kidstime_local", Context.MODE_PRIVATE)

    fun getDailyLimit(dayOfWeek: Int): Int =
        prefs.getInt("limit_$dayOfWeek", 120).coerceIn(0, 1440)

    fun setDailyLimit(dayOfWeek: Int, minutes: Int) {
        prefs.edit().putInt("limit_$dayOfWeek", minutes.coerceIn(0, 1440)).apply()
    }

    fun getAllowedDomains(): List<String> =
        prefs.getStringSet("allowed_domains", emptySet()).orEmpty().toList().sorted()

    fun setAllowedDomains(domains: List<String>) {
        prefs.edit().putStringSet("allowed_domains", domains.toSet()).apply()
    }

    // ---------- PIN ----------

    fun hasPin(): Boolean = prefs.contains("pin_hash") && prefs.contains("pin_salt")

    fun isValidPinFormat(pin: String) = pin.length in 4..12 && pin.all { it.isDigit() }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(derive(pin, salt), Base64.NO_WRAP))
            .putInt("pin_fails", 0)
            .putLong("pin_lock_until", 0L)
            .apply()
    }

    /** Remaining lockout in ms (0 if not locked). Uses wall clock persisted + elapsed realtime guard. */
    fun lockRemainingMs(): Long {
        val until = prefs.getLong("pin_lock_until", 0L)
        val now = System.currentTimeMillis()
        return (until - now).coerceIn(0L, MAX_LOCK_MS)
    }

    fun checkPin(candidate: String): Boolean {
        if (lockRemainingMs() > 0) return false
        val salt = prefs.getString("pin_salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val stored = prefs.getString("pin_hash", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val ok = MessageDigest.isEqual(stored, derive(candidate, salt))
        if (ok) {
            prefs.edit().putInt("pin_fails", 0).putLong("pin_lock_until", 0L).apply()
        } else {
            val fails = prefs.getInt("pin_fails", 0) + 1
            val editor = prefs.edit().putInt("pin_fails", fails)
            if (fails >= 5) {
                // 30s, 60s, 120s ... up to 15 min
                val delay = (30_000L shl (fails - 5).coerceAtMost(5)).coerceAtMost(MAX_LOCK_MS)
                editor.putLong("pin_lock_until", System.currentTimeMillis() + delay)
            }
            editor.apply()
        }
        return ok
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private companion object {
        const val MAX_LOCK_MS = 15 * 60 * 1000L
    }
}
