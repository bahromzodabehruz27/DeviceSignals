package tj.behruz.devicesignals.sdk

import org.json.JSONArray
import org.json.JSONObject

data class DeviceSession(
    val id: String,
    val visitorId: String,
    val firstSeen: String,
    val lastSeen: String,
    val device: Device,
    val events: List<Event> = emptyList(),
) {

    data class Device(
        val deviceId: String,
        val deviceHash: String,
        val os: String = "Android",
        val osVersion: String,
        val appVersion: String,
        val appGuid: String,
        val isEmulator: Boolean,
        val isRooted: Boolean,
        val batteryLevel: Int,
        val screenWidth: Int,
        val carrierName: String?,
        val regionTimezone: String,
        // extended telemetry
        val manufacturer: String?,
        val model: String?,
        val totalRamMb: Long?,
        val cpuCores: Int?,
        val connectionType: String?,
        val isVpnActive: Boolean?,
        val simCountryIso: String?,
    )

    data class Event(
        val type: String,
        val time: String,
    )

    fun toJson(): String {
        val root = JSONObject()
        root.put("id", id)
        root.put("visitor_id", visitorId)
        root.put("first_seen", firstSeen)
        root.put("last_seen", lastSeen)

        val d = JSONObject()
        d.put("device_id", device.deviceId)
        d.put("device_hash", device.deviceHash)
        d.put("os", device.os)
        d.put("os_version", device.osVersion)
        d.put("app_version", device.appVersion)
        d.put("app_guid", device.appGuid)
        d.put("is_emulator", device.isEmulator)
        d.put("is_rooted", device.isRooted)
        d.put("battery_level", device.batteryLevel)
        d.put("screen_width", device.screenWidth)
        d.put("carrier_name", device.carrierName ?: JSONObject.NULL)
        d.put("region_timezone", device.regionTimezone)
        d.put("manufacturer", device.manufacturer ?: JSONObject.NULL)
        d.put("model", device.model ?: JSONObject.NULL)
        d.put("total_ram_mb", device.totalRamMb ?: JSONObject.NULL)
        d.put("cpu_cores", device.cpuCores ?: JSONObject.NULL)
        d.put("connection_type", device.connectionType ?: JSONObject.NULL)
        d.put("is_vpn_active", device.isVpnActive ?: JSONObject.NULL)
        d.put("sim_country_iso", device.simCountryIso ?: JSONObject.NULL)
        root.put("device", d)

        val eventsArray = JSONArray()
        for (event in events) {
            val e = JSONObject()
            e.put("type", event.type)
            e.put("time", event.time)
            eventsArray.put(e)
        }
        root.put("events", eventsArray)

        return root.toString()
    }
}
