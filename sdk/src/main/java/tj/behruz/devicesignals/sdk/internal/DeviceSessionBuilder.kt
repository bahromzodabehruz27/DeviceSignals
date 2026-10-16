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
 * Builds the device-session JSON that is sealed by [tj.behruz.devicesignals.sdk.DeviceSdk.buildDeviceSession]:
 *
 * ```
 * {
 *   "id": "<sessionId>",
 *   "first_seen": "...", "last_seen": "...",
 *   "visitor_id": "<visitorId>",
 *   "device": { ...full flat collect payload..., "device_hash": "..." }
 * }
 * ```
 *
 * `device` is [CollectResult.toJson] with the identity fields that already live on the envelope
 * (`id`, `session_id`, `visitor_id`) removed and `device_hash` added.
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

        val device = JSONObject(collectResult.toJson())
        ENVELOPE_KEYS.forEach { device.remove(it) }
        device.put("device_hash", deviceHash)

        val root = JSONObject()
        root.put("id", collectResult.sessionId)
        root.put("first_seen", firstSeen)
        root.put("last_seen", lastSeen)
        root.put("visitor_id", collectResult.visitorId)
        root.put("device", device)
        return root.toString()
    }

    /** Collect-payload keys that are represented on the session envelope instead of inside `device`. */
    private val ENVELOPE_KEYS = listOf("id", "session_id", "visitor_id")

    private fun formatIso8601Now(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
