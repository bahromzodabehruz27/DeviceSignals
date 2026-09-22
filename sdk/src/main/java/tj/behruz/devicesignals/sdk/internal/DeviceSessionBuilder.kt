package tj.behruz.devicesignals.sdk.internal

import android.os.Build
import tj.behruz.devicesignals.sdk.CollectResult
import tj.behruz.devicesignals.sdk.DeviceSession
import tj.behruz.devicesignals.sdk.Module
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object DeviceSessionBuilder {

    fun build(
        collectResult: CollectResult,
        deviceId: String,
        appGuid: String,
        firstSeen: String,
    ): DeviceSession {
        val moduleData = collectResult.moduleData

        val hardware = moduleData[Module.HARDWARE].orEmpty()
        val app = moduleData[Module.APP].orEmpty()
        val screen = moduleData[Module.SCREEN].orEmpty()
        val integrity = moduleData[Module.INTEGRITY].orEmpty()
        val power = moduleData[Module.POWER].orEmpty()
        val telephony = moduleData[Module.TELEPHONY].orEmpty()
        val network = moduleData[Module.NETWORK].orEmpty()
        val location = moduleData[Module.LOCATION].orEmpty()
        val system = moduleData[Module.SYSTEM].orEmpty()

        val manufacturer = hardware["manufacturer"] as? String ?: Build.MANUFACTURER
        val model = hardware["model"] as? String ?: Build.MODEL

        val deviceHash = DeviceHasher.computeHash(
            manufacturer = manufacturer,
            model = model,
            appGuid = appGuid,
            deviceId = deviceId,
        )

        val lastSeen = formatIso8601Now()

        val device = DeviceSession.Device(
            deviceId = deviceId,
            deviceHash = deviceHash,
            osVersion = system["os_version"] as? String ?: Build.VERSION.RELEASE,
            appVersion = app["version_name"] as? String ?: "",
            appGuid = appGuid,
            isEmulator = hardware["is_emulator"] as? Boolean ?: false,
            isRooted = integrity["is_rooted"] as? Boolean ?: false,
            batteryLevel = (power["battery_level_pct"] as? Number)?.toInt() ?: 0,
            screenWidth = (screen["width_px"] as? Number)?.toInt() ?: 0,
            carrierName = telephony["sim_operator"] as? String,
            regionTimezone = location["timezone_id"] as? String
                ?: TimeZone.getDefault().id,
            manufacturer = manufacturer,
            model = model,
            totalRamMb = (hardware["total_ram_mb"] as? Number)?.toLong(),
            cpuCores = (hardware["cpu_cores"] as? Number)?.toInt(),
            connectionType = network["connection_type"] as? String,
            isVpnActive = network["is_vpn_active"] as? Boolean,
            simCountryIso = telephony["sim_country_iso"] as? String,
        )

        return DeviceSession(
            id = collectResult.sessionId,
            visitorId = collectResult.visitorId,
            firstSeen = firstSeen,
            lastSeen = lastSeen,
            device = device,
        )
    }

    private fun formatIso8601Now(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
