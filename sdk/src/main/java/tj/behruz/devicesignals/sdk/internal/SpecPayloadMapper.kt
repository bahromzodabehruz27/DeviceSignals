package tj.behruz.devicesignals.sdk.internal

import tj.behruz.devicesignals.sdk.CollectStatus
import tj.behruz.devicesignals.sdk.Module

/**
 * Projects raw per-module data onto the flat backend field contract.
 * Pure Kotlin (no Android / org.json) so it can be unit-tested on the JVM.
 */
internal object SpecPayloadMapper {

    data class Mapped(
        val fields: LinkedHashMap<String, Any?>,
        val missingFields: List<String>,
        val collectionStatus: CollectStatus,
    )

    /** Fields filled by the backend, never reported as missing by the SDK. */
    val BACKEND_FIELDS = setOf("device_ip")

    fun map(
        moduleData: Map<Module, Map<String, Any?>>,
        status: CollectStatus,
        sdkVersion: String,
    ): Mapped {
        val app = moduleData[Module.APP].orEmpty()
        val hardware = moduleData[Module.HARDWARE].orEmpty()
        val integrity = moduleData[Module.INTEGRITY].orEmpty()
        val playIntegrity = moduleData[Module.PLAY_INTEGRITY]
        val debug = moduleData[Module.DEBUG].orEmpty()
        val threats = moduleData[Module.THREATS].orEmpty()
        val behavior = moduleData[Module.BEHAVIOR].orEmpty()
        val telephony = moduleData[Module.TELEPHONY].orEmpty()
        val network = moduleData[Module.NETWORK].orEmpty()
        val location = moduleData[Module.LOCATION].orEmpty()
        val power = moduleData[Module.POWER].orEmpty()
        val system = moduleData[Module.SYSTEM].orEmpty()

        val isEmulator = hardware["is_emulator"] as? Boolean
        val isRooted = integrity["is_rooted"] as? Boolean
        val bootloaderState = integrity["bootloader_state"] as? String
        val isTampered = debug["is_runtime_tampering_detected"] as? Boolean
        val chargingType = power["charging_type"] as? String
        val elapsedRealtimeMs = (system["elapsed_realtime_ms"] as? Number)?.toLong()
        val bootTimeMs = (system["boot_time_ms"] as? Number)?.toLong()

        val f = LinkedHashMap<String, Any?>()
        f["platform"] = "Android"
        f["device_id"] = system["drm_id"]
        f["android_id"] = system["android_id"]
        f["app_guid"] = app["app_guid"]
        f["app_version"] = app["version_name"]
        f["sdk_version"] = sdkVersion
        f["android_version"] = system["os_version"]
        f["build_model"] = hardware["model"]
        f["build_manufacturer"] = hardware["manufacturer"]
        f["is_emulator"] = isEmulator
        f["is_rooted"] = isRooted
        f["system_integrity"] = systemIntegrity(isRooted, isEmulator, bootloaderState, isTampered)
        f["bootloader_state"] = bootloaderState
        if (playIntegrity != null) {
            // Verdicts are decoded on the backend from this token.
            f["play_integrity"] = linkedMapOf("token" to playIntegrity["token"])
        }
        f["developer_options_enabled"] = system["developer_options_enabled"]
        f["usb_debugging_enabled"] = debug["is_usb_debugging_enabled"]
        f["is_debugger_attached"] = debug["is_debugger_attached"]
        f["is_app_cloned"] = app["is_app_cloned"]
        f["is_keyguard_secure"] = system["is_keyguard_secure"]
        f["is_debug_build"] = debug["is_debuggable"]
        f["is_runtime_tampering_detected"] = isTampered
        f["is_remote_control_detected"] = threats["is_remote_control_detected"]
        f["is_screen_capture_detected"] = threats["is_screen_being_captured"]
        f["is_overlay_detected"] = threats["is_overlay_detected"]
        f["is_call_active"] = behavior["is_call_active"]
        f["call_detected_last_3_minutes"] = behavior["call_detected_last_3_minutes"]
        f["is_microphone_active"] = behavior["is_microphone_active"]
        f["vpn_enabled"] = network["is_vpn_active"]
        f["proxy_enabled"] = network["is_proxy_configured"]
        f["location_simulated"] = location["is_location_simulated"]
        f["device_ip"] = null
        f["network_type"] = (network["connection_type"] as? String)?.let { if (it == "NONE") "UNKNOWN" else it }
        f["timezone"] = location["timezone_id"]
        f["region_country"] = system["region_country"]
        f["total_storage"] = hardware["total_storage_bytes"]
        f["physical_memory"] = hardware["total_ram_bytes"]
        f["sim_count"] = telephony["active_sim_count"]
        f["esim_count"] = telephony["esim_count"]
        f["battery_charging"] = power["is_charging"]
        f["power_source"] = chargingType?.let { if (it == "NONE") "BATTERY" else it }
        f["usb_connected"] = chargingType?.let { it == "USB" }
        f["system_uptime"] = elapsedRealtimeMs?.let { it / 1000 }
        f["last_boot_time"] = bootTimeMs?.let { it / 1000 }

        val missing = f.filter { (key, value) -> value == null && key !in BACKEND_FIELDS }.keys.toMutableList()
        if (playIntegrity != null && playIntegrity["token"] == null) {
            missing.add("play_integrity.token")
        }

        val collectionStatus = when {
            status == CollectStatus.FAILED -> CollectStatus.FAILED
            missing.isEmpty() -> CollectStatus.SUCCESS
            else -> CollectStatus.PARTIAL
        }
        return Mapped(f, missing, collectionStatus)
    }

    internal fun systemIntegrity(
        isRooted: Boolean?,
        isEmulator: Boolean?,
        bootloaderState: String?,
        isTampered: Boolean?,
    ): String = when {
        isRooted == true || isEmulator == true || isTampered == true || bootloaderState == "UNLOCKED" -> "COMPROMISED"
        isRooted == false && isEmulator == false && isTampered == false && bootloaderState == "LOCKED" -> "TRUSTED"
        else -> "UNKNOWN"
    }
}
