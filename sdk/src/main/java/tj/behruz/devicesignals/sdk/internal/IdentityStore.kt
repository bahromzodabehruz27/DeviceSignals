package tj.behruz.devicesignals.sdk.internal

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal class IdentityStore(private val context: Context) {

    private val prefsRef = AtomicReference<SharedPreferences?>(null)
    private val random = SecureRandom()

    private fun getPrefs(): SharedPreferences {
        prefsRef.get()?.let { return it }
        synchronized(this) {
            prefsRef.get()?.let { return it }
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                "device_signals_identity",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            prefsRef.set(prefs)
            return prefs
        }
    }

    fun getDeviceId(): String {
        val prefs = getPrefs()
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        synchronized(this) {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val id = generateUuidV7()
            prefs.edit().putString(KEY_DEVICE_ID, id).commit()
            return id
        }
    }

    fun getAppGuid(): String {
        val prefs = getPrefs()
        prefs.getString(KEY_APP_GUID, null)?.let { return it }
        synchronized(this) {
            prefs.getString(KEY_APP_GUID, null)?.let { return it }
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_APP_GUID, id).commit()
            return id
        }
    }

    private fun generateUuidV7(): String {
        val timestamp = System.currentTimeMillis()
        val msb = (timestamp shl 16) or
            (0x7000L) or // version 7
            (random.nextLong() and 0x0FFFL)
        val lsb = (0x8000000000000000UL.toLong()) or // variant 10
            (random.nextLong() and 0x3FFFFFFFFFFFFFFFL)
        return UUID(msb, lsb).toString()
    }

    fun getFirstSeen(): String {
        val prefs = getPrefs()
        prefs.getString(KEY_FIRST_SEEN, null)?.let { return it }
        synchronized(this) {
            prefs.getString(KEY_FIRST_SEEN, null)?.let { return it }
            val ts = formatIso8601()
            prefs.edit().putString(KEY_FIRST_SEEN, ts).commit()
            return ts
        }
    }

    private fun formatIso8601(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_APP_GUID = "app_guid"
        private const val KEY_FIRST_SEEN = "first_seen"
    }
}
