package tj.behruz.devicesignals.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tj.behruz.devicesignals.sdk.internal.SpecPayloadMapper

class SpecPayloadMapperTest {

    private val specFields = listOf(
        "platform", "device_id", "android_id", "app_guid", "app_version", "sdk_version",
        "android_version", "build_model", "build_manufacturer", "is_emulator", "is_rooted",
        "system_integrity", "bootloader_state", "developer_options_enabled",
        "usb_debugging_enabled", "is_debugger_attached", "is_app_cloned", "is_keyguard_secure",
        "is_debug_build", "is_runtime_tampering_detected", "is_remote_control_detected",
        "is_screen_capture_detected", "is_overlay_detected", "is_call_active",
        "call_detected_last_3_minutes", "is_microphone_active", "vpn_enabled", "proxy_enabled",
        "location_simulated", "device_ip", "network_type", "timezone", "region_country",
        "total_storage", "physical_memory", "sim_count", "esim_count", "battery_charging",
        "power_source", "usb_connected", "system_uptime", "last_boot_time",
    )

    private val fullData: Map<Module, Map<String, Any?>> = mapOf(
        Module.APP to mapOf("app_guid" to "guid", "version_name" to "6.6.1", "is_app_cloned" to false),
        Module.HARDWARE to mapOf(
            "model" to "SM-S918B", "manufacturer" to "samsung", "is_emulator" to false,
            "total_storage_bytes" to 256_000_000_000L, "total_ram_bytes" to 8_000_000_000L,
        ),
        Module.INTEGRITY to mapOf("is_rooted" to false, "bootloader_state" to "LOCKED"),
        Module.PLAY_INTEGRITY to mapOf("token" to "tok"),
        Module.DEBUG to mapOf(
            "is_usb_debugging_enabled" to false, "is_debugger_attached" to false,
            "is_debuggable" to false, "is_runtime_tampering_detected" to false,
        ),
        Module.THREATS to mapOf(
            "is_remote_control_detected" to false, "is_screen_being_captured" to false,
            "is_overlay_detected" to false,
        ),
        Module.BEHAVIOR to mapOf(
            "is_call_active" to false, "call_detected_last_3_minutes" to true,
            "is_microphone_active" to false,
        ),
        Module.TELEPHONY to mapOf("active_sim_count" to 2, "esim_count" to 1),
        Module.NETWORK to mapOf("is_vpn_active" to false, "is_proxy_configured" to false, "connection_type" to "WIFI"),
        Module.LOCATION to mapOf("is_location_simulated" to false, "timezone_id" to "Asia/Tashkent"),
        Module.POWER to mapOf("is_charging" to true, "charging_type" to "USB"),
        Module.SYSTEM to mapOf(
            "drm_id" to "drm", "android_id" to "aid", "os_version" to "14",
            "developer_options_enabled" to false, "is_keyguard_secure" to true,
            "region_country" to "UZ", "elapsed_realtime_ms" to 90_500L, "boot_time_ms" to 1_700_000_000_999L,
        ),
    )

    @Test
    fun `emits every spec field in spec order`() {
        val mapped = SpecPayloadMapper.map(fullData, CollectStatus.SUCCESS, "1.2.3")
        assertEquals(specFields, mapped.fields.keys.filter { it != "play_integrity" })
    }

    @Test
    fun `full data maps with unit conversions and success status`() {
        val f = SpecPayloadMapper.map(fullData, CollectStatus.SUCCESS, "1.2.3")
        assertEquals("Android", f.fields["platform"])
        assertEquals("drm", f.fields["device_id"])
        assertEquals("1.2.3", f.fields["sdk_version"])
        assertEquals("TRUSTED", f.fields["system_integrity"])
        assertEquals(256_000_000_000L, f.fields["total_storage"])
        assertEquals(90L, f.fields["system_uptime"])
        assertEquals(1_700_000_000L, f.fields["last_boot_time"])
        assertEquals("USB", f.fields["power_source"])
        assertEquals(true, f.fields["usb_connected"])
        assertEquals(mapOf("token" to "tok"), f.fields["play_integrity"])
        assertTrue(f.missingFields.isEmpty())
        assertEquals(CollectStatus.SUCCESS, f.collectionStatus)
    }

    @Test
    fun `device_ip is backend-owned and never reported missing`() {
        val f = SpecPayloadMapper.map(fullData, CollectStatus.SUCCESS, "1")
        assertNull(f.fields["device_ip"])
        assertFalse("device_ip" in f.missingFields)
    }

    @Test
    fun `null fields are listed as missing and status becomes partial`() {
        val data = fullData - Module.BEHAVIOR
        val f = SpecPayloadMapper.map(data, CollectStatus.SUCCESS, "1")
        assertEquals(
            listOf("is_call_active", "call_detected_last_3_minutes", "is_microphone_active"),
            f.missingFields,
        )
        assertEquals(CollectStatus.PARTIAL, f.collectionStatus)
    }

    @Test
    fun `battery-only power and no network map to spec enums`() {
        val data = fullData +
            (Module.POWER to mapOf("is_charging" to false, "charging_type" to "NONE")) +
            (Module.NETWORK to mapOf("is_vpn_active" to false, "is_proxy_configured" to false, "connection_type" to "NONE"))
        val f = SpecPayloadMapper.map(data, CollectStatus.SUCCESS, "1")
        assertEquals("BATTERY", f.fields["power_source"])
        assertEquals(false, f.fields["usb_connected"])
        assertEquals("UNKNOWN", f.fields["network_type"])
    }

    @Test
    fun `system integrity verdicts`() {
        assertEquals("COMPROMISED", SpecPayloadMapper.systemIntegrity(true, false, "LOCKED", false))
        assertEquals("COMPROMISED", SpecPayloadMapper.systemIntegrity(false, false, "UNLOCKED", false))
        assertEquals("TRUSTED", SpecPayloadMapper.systemIntegrity(false, false, "LOCKED", false))
        assertEquals("UNKNOWN", SpecPayloadMapper.systemIntegrity(false, false, "UNKNOWN", false))
        assertEquals("UNKNOWN", SpecPayloadMapper.systemIntegrity(null, false, "LOCKED", false))
    }

    @Test
    fun `failed collection stays failed`() {
        val f = SpecPayloadMapper.map(emptyMap(), CollectStatus.FAILED, "1")
        assertEquals(CollectStatus.FAILED, f.collectionStatus)
    }
}
