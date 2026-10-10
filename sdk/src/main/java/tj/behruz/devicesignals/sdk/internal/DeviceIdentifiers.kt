package tj.behruz.devicesignals.sdk.internal

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import java.util.UUID

internal object DeviceIdentifiers {

    private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

    @Volatile
    private var cachedDrmId: String? = null

    /** SHA-256 of the Widevine device unique ID, or null if Widevine is unavailable. */
    fun drmId(): String? {
        cachedDrmId?.let { return it }
        var drm: MediaDrm? = null
        return try {
            drm = MediaDrm(WIDEVINE_UUID)
            val raw = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            DeviceHasher.sha256(raw.joinToString("") { "%02x".format(it) }).also { cachedDrmId = it }
        } catch (_: Exception) {
            null
        } finally {
            if (Build.VERSION.SDK_INT >= 28) drm?.close() else @Suppress("DEPRECATION") drm?.release()
        }
    }

    @SuppressLint("HardwareIds")
    fun androidId(context: Context): String? = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)?.ifEmpty { null }
    } catch (_: Exception) {
        null
    }
}
