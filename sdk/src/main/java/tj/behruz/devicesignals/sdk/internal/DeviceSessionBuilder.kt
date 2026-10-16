package tj.behruz.devicesignals.sdk.internal

import android.os.Build
import org.json.JSONObject
import tj.behruz.devicesignals.sdk.CollectResult
import tj.behruz.devicesignals.sdk.Module
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the device-session JSON that is sealed by [tj.behruz.devicesignals.sdk.DeviceSdk.buildDeviceSession].
 *
 * The payload is the full flat collect payload (identical to [CollectResult.toJson]) extended with
 * session identity fields: `device_hash`, `first_seen`, `last_seen`.
 */
internal object DeviceSessionBuilder {

    fun buildJson(
        collectResult: CollectResult,
        deviceId: String,
        appGuid: String,
        firstSeen: String,
    ): String {
        val hardware = collectResult.moduleData[Module.HARDWARE].orEmpty()
        val manufacturer = hardware["manufacturer"] as? String ?: Build.MANUFACTURER
        val model = hardware["model"] as? String ?: Build.MODEL

        val deviceHash = DeviceHasher.computeHash(
            manufacturer = manufacturer,
            model = model,
            appGuid = appGuid,
            deviceId = deviceId,
        )

        val lastSeen = formatIso8601Now()
        SdkLog.debug {
            "session identity: device_hash=${deviceHash.take(12)}... " +
                "(from $manufacturer|$model|app_guid|device_id) first_seen=$firstSeen last_seen=$lastSeen"
        }

        val root = JSONObject(collectResult.toJson())
        root.put("device_hash", deviceHash)
        root.put("first_seen", firstSeen)
        root.put("last_seen", lastSeen)
        return root.toString()
    }

    private fun formatIso8601Now(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
