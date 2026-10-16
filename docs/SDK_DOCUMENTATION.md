# DeviceSignals SDK Documentation

Android SDK for collecting device fingerprints, integrity signals, and fraud detection data.

**Maven coordinates:** `io.github.bahromzodabehruz27:device-signals-sdk:0.4.0`

**Min SDK:** 24 (Android 7.0)

---

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Initialization](#initialization)
- [Collecting Signals](#collecting-signals)
- [Building Device Sessions](#building-device-sessions)
- [Activity Attachment](#activity-attachment)
- [Configuration](#configuration)
- [Modules](#modules)
- [JSON Output Format](#json-output-format)
- [Module Data Reference](#module-data-reference)
- [Permissions](#permissions)
- [ProGuard / R8](#proguard--r8)
- [Error Handling](#error-handling)
- [Java Interop](#java-interop)
- [Thread Safety](#thread-safety)

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.bahromzodabehruz27:device-signals-sdk:0.4.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'io.github.bahromzodabehruz27:device-signals-sdk:0.4.0'
}
```

### Optional: Play Integrity

If you want Play Integrity token collection, also add:

```kotlin
dependencies {
    implementation("com.google.android.play:integrity:1.4.0")
}
```

The SDK uses reflection to access Play Integrity, so it works with or without this dependency.

---

## Quick Start

```kotlin
// 1. Initialize in Application.onCreate() or before first use
DeviceSdk.init(context)

// 2. Attach an activity (required for overlay/threat detection)
DeviceSdk.attach(activity)

// 3. Collect device signals
val result = DeviceSdk.collect(
    sessionId = "your-session-id",
    visitorId = "your-visitor-id",
)

// 4. Get JSON payload
val json = result.toJson()
```

---

## Initialization

Call `DeviceSdk.init()` once before any other SDK method. The recommended place is `Application.onCreate()` or your launcher activity's `onCreate()`.

```kotlin
DeviceSdk.init(context)
```

With custom configuration:

```kotlin
DeviceSdk.init(
    context = applicationContext,
    config = SdkConfig(
        timeoutMs = 5_000L,
        enabledModules = setOf(Module.APP, Module.HARDWARE, Module.INTEGRITY),
        logLevel = LogLevel.DEBUG,
        logger = object : SdkLogger {
            override fun log(level: LogLevel, tag: String, message: String) {
                Log.d(tag, "[$level] $message")
            }
        },
    ),
)
```

Calling `init()` again is safe -- it resets the SDK with the new configuration and clears all cached module data.

---

## Collecting Signals

### Kotlin (coroutines)

```kotlin
// Collect all modules
val result: CollectResult = DeviceSdk.collect(
    sessionId = "session-123",
    visitorId = "visitor-456",
)

// Collect specific modules only
val result = DeviceSdk.collect(
    sessionId = "session-123",
    visitorId = "visitor-456",
    modules = setOf(Module.APP, Module.HARDWARE, Module.NETWORK),
)
```

### Callback API (Kotlin or Java)

```kotlin
DeviceSdk.collect("session-123", "visitor-456") { result ->
    // Called on a background thread
    val json = result.toJson()
}
```

With specific modules:

```kotlin
DeviceSdk.collect(
    sessionId = "session-123",
    visitorId = "visitor-456",
    modules = setOf(Module.APP, Module.INTEGRITY),
    callback = CollectCallback { result ->
        // handle result
    },
)
```

### CollectResult

| Property | Type | Description |
|----------|------|-------------|
| `status` | `CollectStatus` | `SUCCESS`, `PARTIAL`, or `FAILED` |
| `moduleData` | `Map<Module, Map<String, Any?>>` | Raw signal data keyed by module |
| `missingFields` | `List<String>` | Fields that could not be collected (format: `module.field`) |
| `durationMs` | `Long` | Total collection time in milliseconds |

```kotlin
when (result.status) {
    CollectStatus.SUCCESS -> // All requested modules collected fully
    CollectStatus.PARTIAL -> // Some fields missing (check result.missingFields)
    CollectStatus.FAILED  -> // No data collected (timeout or all modules failed)
}

// Serialize to JSON
val json: String = result.toJson()

// Access individual module data
val isRooted = result.moduleData[Module.INTEGRITY]?.get("is_rooted") as? Boolean
val model = result.moduleData[Module.HARDWARE]?.get("model") as? String
```

---

## Building Device Sessions

`buildDeviceSession()` collects all signals, builds the full collect payload plus session identity fields, encrypts it with a public key (NaCl sealed box), and returns the result as a base64 string. This is intended for sending to your backend for server-side fraud analysis.

**Requirements:** `deviceId` and `fraudPublicKeyBase64` must be set in `SdkConfig`.

```kotlin
DeviceSdk.init(
    context = applicationContext,
    config = SdkConfig(
        deviceId = "your-device-id",
        fraudPublicKeyBase64 = "base64-encoded-x25519-public-key",
    ),
)

// Returns base64-encoded sealed box
val encryptedSession: String = DeviceSdk.buildDeviceSession(
    sessionId = "session-123",
    visitorId = "visitor-456",
)

// Send encryptedSession to your backend
api.submitDeviceSession(encryptedSession)
```

### Inspecting a Session On-Device (debug)

`DeviceSessionCrypto` lets a demo / QA build generate a throwaway keypair and open the sealed payload locally to see exactly what is sent. Never ship a real backend private key in an app.

```kotlin
val keys = DeviceSessionCrypto.generateKeyPair()
DeviceSdk.init(context, SdkConfig(deviceId = "dev-1", fraudPublicKeyBase64 = keys.publicKeyBase64))

val sealed = DeviceSdk.buildDeviceSession("session-1", "visitor-1")
val json = DeviceSessionCrypto.open(sealed, keys.publicKeyBase64, keys.privateKeyBase64)
```

`open` throws `DeviceSdkException.EncryptionException` if the key does not match (MAC verification fails).

### Key Generation

The public key must be a 32-byte X25519 public key, base64-encoded. Generate a keypair on your server:

```python
# Python (PyNaCl)
from nacl.public import PrivateKey
import base64

private_key = PrivateKey.generate()
public_key = private_key.public_key
print("Public:", base64.b64encode(bytes(public_key)).decode())
print("Private:", base64.b64encode(bytes(private_key)).decode())
```

### Device Session Payload

The encrypted payload, once decrypted on your server, is an envelope with session identity fields and a `device` object. `device` is the **same flat JSON as `CollectResult.toJson()`** (all spec fields, `collection_status`, `missing_fields`, etc.) minus the identity keys that live on the envelope (`id`, `session_id`, `visitor_id`), plus `device_hash`.

| Field | Description |
|-------|-------------|
| `id` | The `sessionId` passed to `buildDeviceSession` |
| `first_seen` | First time the SDK ran on this install (ISO 8601 UTC) |
| `last_seen` | Time this session was built (ISO 8601 UTC) |
| `visitor_id` | The `visitorId` passed to `buildDeviceSession` |
| `device.device_hash` | SHA-256 of `manufacturer|model|app_guid|SdkConfig.deviceId` -- stable across sessions |

```json
{
  "id": "session-123",
  "first_seen": "2024-01-15T10:30:00Z",
  "last_seen": "2024-03-22T14:20:00Z",
  "visitor_id": "visitor-456",
  "device": {
    "schema_version": "2.0",
    "collected_at": "2024-03-22T14:20:00.123Z",
    "first_open": "2024-03-22T14:19:58.900Z",
    "last_open": "2024-03-22T14:20:00.123Z",
    "collection_duration_ms": 1223,
    "platform": "Android",
    "device_id": "drm-id",
    "android_id": "android-id",
    "app_guid": "uuid-v4",
    "app_version": "1.2.3",
    "sdk_version": "0.4.0",
    "android_version": "14",
    "build_model": "SM-S911B",
    "build_manufacturer": "Samsung",
    "is_emulator": false,
    "is_rooted": false,
    "system_integrity": "TRUSTED",
    "...": "all other collect fields",
    "collection_status": "SUCCESS",
    "missing_fields": [],
    "device_hash": "sha256-hash"
  }
}
```

### Inspecting a Session On-Device (debug)

`DeviceSessionCrypto` lets a demo / QA build generate a throwaway keypair and open the sealed payload locally to see exactly what is sent. Never ship a real backend private key in an app.

```kotlin
val keys = DeviceSessionCrypto.generateKeyPair()
DeviceSdk.init(context, SdkConfig(deviceId = "dev-1", fraudPublicKeyBase64 = keys.publicKeyBase64))

val sealed = DeviceSdk.buildDeviceSession("session-1", "visitor-1")
val json = DeviceSessionCrypto.open(sealed, keys.publicKeyBase64, keys.privateKeyBase64)
```

`open` throws `DeviceSdkException.EncryptionException` if the key does not match (MAC verification fails).

### Key Generation

The public key must be a 32-byte X25519 public key, base64-encoded. Generate a keypair on your server:

```python
# Python (PyNaCl)
from nacl.public import PrivateKey
import base64

private_key = PrivateKey.generate()
public_key = private_key.public_key
print("Public:", base64.b64encode(bytes(public_key)).decode())
print("Private:", base64.b64encode(bytes(private_key)).decode())
```

### Device Session Payload

The encrypted payload, once decrypted on your server, is the **same flat JSON as `CollectResult.toJson()`** (all spec fields, `collection_status`, `missing_fields`, etc.) extended with three session identity fields:

| Field | Description |
|-------|-------------|
| `device_hash` | SHA-256 of `manufacturer|model|app_guid|SdkConfig.deviceId` -- stable across sessions |
| `first_seen` | First time the SDK ran on this install (ISO 8601 UTC) |
| `last_seen` | Time this session was built (ISO 8601 UTC) |

```json
{
  "schema_version": "2.0",
  "id": "uuid-v4",
  "session_id": "session-123",
  "visitor_id": "visitor-456",
  "collected_at": "2024-03-22T14:20:00.123Z",
  "first_open": "2024-03-22T14:19:58.900Z",
  "last_open": "2024-03-22T14:20:00.123Z",
  "collection_duration_ms": 1223,
  "platform": "Android",
  "device_id": "drm-id",
  "android_id": "android-id",
  "app_guid": "uuid-v4",
  "app_version": "1.2.3",
  "sdk_version": "1.0.0",
  "android_version": "14",
  "build_model": "SM-S911B",
  "build_manufacturer": "Samsung",
  "is_emulator": false,
  "is_rooted": false,
  "system_integrity": "TRUSTED",
  "...": "all other collect fields",
  "collection_status": "SUCCESS",
  "missing_fields": [],
  "device_hash": "sha256-hash",
  "first_seen": "2024-01-15T10:30:00Z",
  "last_seen": "2024-03-22T14:20:00Z"
}
```

---

## Activity Attachment

Attach an activity to enable threat detection features that require a window reference (overlay detection):

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceSdk.attach(this)
    }
}
```

The SDK automatically detaches when the activity is destroyed (via `LifecycleObserver`). You can also detach manually:

```kotlin
DeviceSdk.detach()
```

If no activity is attached, the `threats` module will still collect most signals but will report `is_overlay_detected` and `is_screen_being_captured` as missing fields.

---

## Configuration

### SdkConfig

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `timeoutMs` | `Long` | `3000` | Global timeout for the entire collect operation |
| `moduleTimeoutMs` | `Long` | `1500` | Per-module timeout |
| `enabledModules` | `Set<Module>` | `Module.ALL` | Which modules to enable globally |
| `playIntegrityCloudProjectNumber` | `String?` | `null` | Google Cloud project number for Play Integrity |
| `logLevel` | `LogLevel` | `NONE` | Minimum log level: `NONE`, `ERROR`, `WARN`, `DEBUG` |
| `logger` | `SdkLogger?` | `null` | Custom logger implementation |
| `onModuleCompleted` | `((Module, Long, ModuleStatus) -> Unit)?` | `null` | Callback invoked after each module completes |
| `deviceId` | `String?` | `null` | Device identifier (required for `buildDeviceSession`) |
| `fraudPublicKeyBase64` | `String?` | `null` | X25519 public key for encryption (required for `buildDeviceSession`) |

### Module Completion Callback

Monitor per-module performance:

```kotlin
DeviceSdk.init(
    context = this,
    config = SdkConfig(
        onModuleCompleted = { module, elapsedMs, status ->
            Log.d("SDK", "${module.name}: $status in ${elapsedMs}ms")
        },
    ),
)
```

### Logging

Logging is off by default (`logLevel = LogLevel.NONE`). To see what the SDK collects, enable it with the built-in logcat logger (tag `DeviceSignals`):

```kotlin
val config = SdkConfig(
    logLevel = LogLevel.DEBUG,
    logger = LogcatLogger,
)
```

What is logged at each level:

| Level | Events |
|-------|--------|
| `ERROR` | A module threw, collection failed entirely, session build / callback collect failed |
| `WARN` | Global collect timeout hit (lists which modules did and did not finish) |
| `DEBUG` | `init` summary (modules, Play Integrity, deviceId / fraud key presence), collect start (requested / skipped modules, timeouts), per-module status + full `key=value` dump of collected data, collect summary (status, duration, missing fields), payload summary + all spec fields, session identity (`device_hash`, `first_seen`, `last_seen`) and sealed payload sizes |

DEBUG dumps include raw signal values (`android_id`, DRM id, SIM data, etc.). Keep it off in production builds.

### Custom Logger

```kotlin
val config = SdkConfig(
    logLevel = LogLevel.DEBUG,
    logger = object : SdkLogger {
        override fun log(level: LogLevel, tag: String, message: String) {
            when (level) {
                LogLevel.ERROR -> Log.e(tag, message)
                LogLevel.WARN -> Log.w(tag, message)
                LogLevel.DEBUG -> Log.d(tag, message)
                LogLevel.NONE -> { }
            }
        }
    },
)
```

---

## Modules

All modules run in parallel with independent timeouts. If a module fails or times out, the SDK continues collecting from the remaining modules and returns a `PARTIAL` result.

| Module | Enum | JSON key | Static | Description |
|--------|------|----------|--------|-------------|
| App | `Module.APP` | `app` | Yes | Application metadata and install info |
| Hardware | `Module.HARDWARE` | `hardware` | Yes | Device hardware specs, emulator detection |
| Screen | `Module.SCREEN` | `screen` | Yes | Display resolution, density, refresh rate |
| Integrity | `Module.INTEGRITY` | `integrity` | No | Root detection, bootloader, SELinux |
| Play Integrity | `Module.PLAY_INTEGRITY` | `play_integrity` | No | Google Play Integrity token |
| Debug | `Module.DEBUG` | `debug` | No | Debugger, Frida, Xposed detection |
| Threats | `Module.THREATS` | `threats` | No | Overlay, accessibility, remote control |
| Telephony | `Module.TELEPHONY` | `telephony` | No | SIM info, carrier, phone type |
| Network | `Module.NETWORK` | `network` | No | Connection type, VPN, WiFi, proxy |
| Location | `Module.LOCATION` | `location` | No | Coarse location, timezone, providers |
| Power | `Module.POWER` | `power` | No | Battery level, charging state |
| System | `Module.SYSTEM` | `system` | No | OS version, uptime, language |

**Static modules** are cached after the first collection since their values don't change during the app lifecycle (e.g., hardware specs, app version). Non-static modules are re-collected every time.

### Selecting Modules

```kotlin
// Collect only specific modules
val result = DeviceSdk.collect(
    sessionId = "s1",
    visitorId = "v1",
    modules = setOf(Module.APP, Module.HARDWARE, Module.INTEGRITY),
)

// Disable specific modules globally via config
DeviceSdk.init(
    context = this,
    config = SdkConfig(
        enabledModules = Module.ALL - setOf(Module.LOCATION, Module.TELEPHONY),
    ),
)
```

The effective modules for any `collect()` call is the intersection of `config.enabledModules` and the `modules` parameter.

---

## JSON Output Format

`CollectResult.toJson()` produces a flat JSON payload matching the backend field contract. Raw per-module data (documented below) is still available via `CollectResult.moduleData`.

```json
{
  "schema_version": "2.0",
  "id": "random-uuid",
  "session_id": "your-session-id",
  "visitor_id": "your-visitor-id",
  "collected_at": "2026-10-09T04:56:58.152Z",
  "collection_duration_ms": 89,
  "platform": "Android",
  "device_id": "sha256-of-widevine-drm-id",
  "android_id": "24b31bc1f7f1ee73",
  "app_guid": "afa0857f-...",
  "app_version": "6.6.1",
  "sdk_version": "0.1.1",
  "android_version": "16",
  "build_model": "SM-S938B",
  "build_manufacturer": "samsung",
  "is_emulator": false,
  "is_rooted": false,
  "system_integrity": "TRUSTED",
  "bootloader_state": "LOCKED",
  "play_integrity": { "token": "..." },
  "developer_options_enabled": true,
  "usb_debugging_enabled": true,
  "is_debugger_attached": false,
  "is_app_cloned": false,
  "is_keyguard_secure": true,
  "is_debug_build": false,
  "is_runtime_tampering_detected": false,
  "is_remote_control_detected": false,
  "is_screen_capture_detected": false,
  "is_overlay_detected": false,
  "is_call_active": false,
  "call_detected_last_3_minutes": false,
  "is_microphone_active": false,
  "vpn_enabled": false,
  "proxy_enabled": false,
  "location_simulated": false,
  "device_ip": null,
  "network_type": "WIFI",
  "timezone": "Asia/Tashkent",
  "region_country": "UZ",
  "total_storage": 234073620480,
  "physical_memory": 11653091328,
  "sim_count": 2,
  "esim_count": 1,
  "battery_charging": true,
  "power_source": "USB",
  "usb_connected": true,
  "system_uptime": 468115,
  "last_boot_time": 1791053702,
  "collection_status": "SUCCESS",
  "missing_fields": []
}
```

Notes:
- `play_integrity` is present only when `playIntegrityCloudProjectNumber` is configured. The backend decodes the token into `play_integrity.app_integrity`, `device_integrity` and `app_access_risk.*`.
- `device_ip` is always `null`; the backend fills it from the request IP.
- `total_storage` / `physical_memory` are bytes; `system_uptime` is seconds; `last_boot_time` is a Unix timestamp in seconds.
- `system_integrity` is `COMPROMISED` if root, emulator, runtime tampering or an unlocked bootloader is detected; `TRUSTED` if all are confirmed clean and the bootloader is locked; otherwise `UNKNOWN`.
- `missing_fields` lists every SDK-owned field that is `null`; any entry makes `collection_status` `PARTIAL`.

---

## Module Data Reference

### `app`

| Field | Type | Description |
|-------|------|-------------|
| `app_name` | `String` | Application label |
| `package_name` | `String` | Package name |
| `version_name` | `String` | Version name (e.g., `"1.2.3"`) |
| `version_code` | `Long` | Version code |
| `install_source` | `String?` | Installing package (e.g., `"com.android.vending"`) |
| `first_install_time` | `Long` | First install timestamp (epoch ms) |
| `last_update_time` | `Long` | Last update timestamp (epoch ms) |
| `is_system_app` | `Boolean` | Whether the app is a system app |
| `app_guid` | `String` | Persistent app installation GUID (UUID v4) |

### `hardware`

| Field | Type | Description |
|-------|------|-------------|
| `manufacturer` | `String` | Device manufacturer (e.g., `"Samsung"`) |
| `brand` | `String` | Device brand |
| `model` | `String` | Device model (e.g., `"SM-S911B"`) |
| `device` | `String` | Device codename |
| `board` | `String` | Board name |
| `hardware` | `String` | Hardware name |
| `soc_manufacturer` | `String?` | SoC manufacturer (API 31+, null otherwise) |
| `soc_model` | `String?` | SoC model (API 31+, null otherwise) |
| `total_ram_mb` | `Long` | Total RAM in MB |
| `total_storage_mb` | `Long` | Total storage in MB |
| `cpu_cores` | `Int` | Number of CPU cores |
| `cpu_architecture` | `String` | Primary CPU ABI (e.g., `"arm64-v8a"`) |
| `is_emulator` | `Boolean` | `true` if 2+ emulator signals detected |

### `screen`

| Field | Type | Description |
|-------|------|-------------|
| `width_px` | `Int` | Screen width in pixels |
| `height_px` | `Int` | Screen height in pixels |
| `density_dpi` | `Int` | Screen density in DPI |
| `density_bucket` | `String` | Density bucket: `ldpi`, `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi` |
| `refresh_rate_hz` | `Float` | Display refresh rate |
| `orientation` | `String` | `"portrait"` or `"landscape"` |
| `font_scale` | `Float` | User's font scale setting |

### `integrity`

| Field | Type | Description |
|-------|------|-------------|
| `is_rooted` | `Boolean` | `true` if any root indicator is found |
| `root_indicators` | `List<String>` | Detected root artifacts (su paths, packages, etc.) |
| `bootloader_state` | `String` | `"LOCKED"`, `"UNLOCKED"`, or `"UNKNOWN"` |
| `se_linux_status` | `String` | `"ENFORCING"`, `"PERMISSIVE"`, or `"DISABLED"` |
| `is_verified_boot` | `Boolean` | Whether verified boot state is "green" |

Root detection checks:
- `su` binary paths (`/system/bin/su`, `/system/xbin/su`, etc.)
- Root management apps (Magisk, SuperSU, etc.)
- Build tags containing `test-keys`
- Writable `/system` partition
- Magisk directory (`/sbin/.magisk`)

### `play_integrity`

| Field | Type | Description |
|-------|------|-------------|
| `token` | `String?` | Play Integrity token (null if unavailable) |

Requires `playIntegrityCloudProjectNumber` in `SdkConfig` and the Play Integrity library on the classpath. Tokens are cached for 5 minutes.

### `debug`

| Field | Type | Description |
|-------|------|-------------|
| `is_debuggable` | `Boolean` | Whether the app has `FLAG_DEBUGGABLE` |
| `is_debugger_attached` | `Boolean` | Whether a debugger is currently connected |
| `is_usb_debugging_enabled` | `Boolean` | Whether ADB/USB debugging is enabled |
| `is_runtime_tampering_detected` | `Boolean` | `true` if any tampering indicator is found |
| `tampering_indicators` | `List<String>` | Detected tampering artifacts |

Tampering detection checks:
- Frida libraries in `/proc/self/maps`
- Xposed framework classes
- Non-zero `TracerPid` in `/proc/self/status`
- Frida default port (27042) listening

### `threats`

| Field | Type | Description |
|-------|------|-------------|
| `installed_threat_apps` | `List<String>` | Detected RAT packages (TeamViewer, AnyDesk, etc.) |
| `is_overlay_detected` | `Boolean?` | Whether a screen overlay is detected (requires Activity) |
| `is_screen_being_captured` | `Boolean?` | Reserved; currently always `null` |
| `is_accessibility_service_active` | `Boolean` | Whether any accessibility service is enabled |
| `accessibility_services` | `List<String>` | List of enabled accessibility services (`package/class`) |
| `is_remote_control_detected` | `Boolean` | Whether RAT apps are installed |

Fields marked `?` will be `null` (and listed in `missingFields`) if no Activity is attached via `DeviceSdk.attach()`.

### `telephony`

| Field | Type | Description |
|-------|------|-------------|
| `sim_operator` | `String?` | SIM operator MCC+MNC code |
| `sim_country_iso` | `String?` | SIM country ISO code |
| `network_operator` | `String?` | Network operator MCC+MNC code |
| `network_country_iso` | `String?` | Network country ISO code |
| `phone_type` | `String` | `"GSM"`, `"CDMA"`, `"SIP"`, or `"NONE"` |
| `sim_state` | `String` | `"READY"`, `"ABSENT"`, `"PIN_REQUIRED"`, `"PUK_REQUIRED"`, `"NETWORK_LOCKED"`, or `"UNKNOWN"` |
| `is_multi_sim` | `Boolean?` | Whether multiple SIMs are active (API 29+) |
| `active_sim_count` | `Int?` | Number of active SIM subscriptions (API 29+) |

Requires `READ_PHONE_STATE` permission for operator and SIM details. Without it, these fields appear in `missingFields`.

### `network`

| Field | Type | Description |
|-------|------|-------------|
| `connection_type` | `String` | `"WIFI"`, `"CELLULAR"`, `"VPN"`, `"ETHERNET"`, `"NONE"`, or `"UNKNOWN"` |
| `is_vpn_active` | `Boolean` | Whether a VPN transport is active |
| `vpn_interfaces` | `List<String>` | Detected VPN network interfaces (tun, pptp, ppp) |
| `wifi_ssid_hash` | `String?` | SHA-256 hash of the WiFi SSID (requires location permission) |
| `wifi_bssid_hash` | `String?` | SHA-256 hash of the WiFi BSSID (requires location permission) |
| `wifi_frequency_mhz` | `Int?` | WiFi frequency in MHz |
| `proxy_host` | `String?` | Configured HTTP proxy host |
| `proxy_port` | `Int?` | Configured HTTP proxy port |
| `is_proxy_configured` | `Boolean` | Whether an HTTP proxy is configured |

WiFi details require `ACCESS_FINE_LOCATION` permission. SSID and BSSID are hashed (never sent in plaintext).

### `location`

| Field | Type | Description |
|-------|------|-------------|
| `is_location_enabled` | `Boolean` | Whether location services are enabled |
| `available_providers` | `List<String>` | Active location providers (e.g., `["gps", "network"]`) |
| `latitude_coarse` | `Double?` | Coarse latitude (requires `ACCESS_COARSE_LOCATION`) |
| `longitude_coarse` | `Double?` | Coarse longitude |
| `accuracy_m` | `Float?` | Location accuracy in meters |
| `timezone_id` | `String` | Timezone ID (e.g., `"America/New_York"`) |
| `timezone_offset_minutes` | `Int` | UTC offset in minutes |

Location coordinates require `ACCESS_COARSE_LOCATION` permission. The SDK uses `getLastKnownLocation` -- it does not request fresh GPS fixes.

### `power`

| Field | Type | Description |
|-------|------|-------------|
| `battery_level_pct` | `Int` | Battery percentage (0-100) |
| `is_charging` | `Boolean` | Whether the device is charging |
| `charging_type` | `String` | `"USB"`, `"AC"`, `"WIRELESS"`, or `"NONE"` |
| `battery_health` | `String` | `"GOOD"`, `"OVERHEAT"`, `"DEAD"`, `"OVER_VOLTAGE"`, `"COLD"`, or `"UNKNOWN"` |
| `is_power_save_mode` | `Boolean` | Whether battery saver is active |
| `is_battery_present` | `Boolean` | Whether a battery is present |

### `system`

| Field | Type | Description |
|-------|------|-------------|
| `os_version` | `String` | Android version (e.g., `"14"`) |
| `api_level` | `Int` | Android API level (e.g., `34`) |
| `build_fingerprint` | `String` | Full build fingerprint |
| `build_tags` | `String` | Build tags (e.g., `"release-keys"`) |
| `build_type` | `String` | Build type (e.g., `"user"`) |
| `security_patch_level` | `String` | Security patch date (e.g., `"2024-03-01"`) |
| `device_id` | `String` | Persistent device ID (UUIDv7, stored encrypted) |
| `language` | `String` | Device language tag (e.g., `"en-US"`) |
| `uptime_ms` | `Long` | System uptime in milliseconds |
| `elapsed_realtime_ms` | `Long` | Time since boot including sleep |

---

## Permissions

### Included in the SDK manifest (automatic)

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Network connectivity checks |
| `ACCESS_NETWORK_STATE` | Connection type detection |

### Optional (declared by your app)

| Permission | Modules affected | Fields unlocked |
|------------|-----------------|-----------------|
| `READ_PHONE_STATE` | `telephony` | `sim_operator`, `sim_country_iso`, `network_operator`, `network_country_iso`, `is_multi_sim`, `active_sim_count` |
| `ACCESS_COARSE_LOCATION` | `location` | `latitude_coarse`, `longitude_coarse`, `accuracy_m` |
| `ACCESS_FINE_LOCATION` | `network` | `wifi_ssid_hash`, `wifi_bssid_hash`, `wifi_frequency_mhz` |

The SDK never requests permissions. If a permission is not granted, the affected fields are reported as `null` and listed in `CollectResult.missingFields`. All other fields in that module are still collected.

---

## ProGuard / R8

The SDK ships with consumer ProGuard rules that are automatically applied to your app. No manual configuration is needed.

The rules keep all public API classes:
- `DeviceSdk`, `SdkConfig`, `CollectResult`, `CollectStatus`
- `Module`, `ModuleStatus`, `LogLevel`
- `SdkLogger`, `LogcatLogger`, `CollectCallback`
- `DeviceSessionCrypto` (debug: generate keypair, open sealed session)
- `DeviceSdkException` and its subclasses

---

## Error Handling

All SDK exceptions extend `DeviceSdkException` (a `RuntimeException`):

| Exception | When |
|-----------|------|
| `DeviceSdkException.NotInitializedException` | Any method called before `DeviceSdk.init()` |
| `DeviceSdkException.InvalidArgumentException` | Empty/blank `sessionId` or `visitorId`, IDs exceeding 128 characters, missing `deviceId` or `fraudPublicKeyBase64` for `buildDeviceSession` |
| `DeviceSdkException.EncryptionException` | Encryption failure (invalid public key, MAC verification failure) |

Individual module failures do **not** throw exceptions -- they are captured internally and reflected in `CollectResult.status` and `missingFields`.

```kotlin
try {
    val result = DeviceSdk.collect("session-1", "visitor-1")
    // result.status may be PARTIAL even on success
} catch (e: DeviceSdkException.NotInitializedException) {
    // Call DeviceSdk.init() first
} catch (e: DeviceSdkException.InvalidArgumentException) {
    // Fix the arguments
}
```

---

## Java Interop

All public methods are annotated with `@JvmStatic` for seamless Java usage.

### Java: Callback API

```java
DeviceSdk.init(context, new SdkConfig());

DeviceSdk.collect("session-1", "visitor-1", result -> {
    String json = result.toJson();
    // process on background thread
});
```

### Java: Module selection

```java
Set<Module> modules = new HashSet<>();
modules.add(Module.APP);
modules.add(Module.HARDWARE);

DeviceSdk.collect("session-1", "visitor-1", modules, result -> {
    // handle result
});
```

### Java: Configuration

```java
SdkConfig config = new SdkConfig(
    5000L,              // timeoutMs
    2000L,              // moduleTimeoutMs
    Module.ALL,         // enabledModules
    null,               // playIntegrityCloudProjectNumber
    LogLevel.DEBUG,     // logLevel
    null,               // logger
    null,               // onModuleCompleted
    "my-device-id",     // deviceId
    "base64-key"        // fraudPublicKeyBase64
);

DeviceSdk.init(context, config);
```

---

## Thread Safety

- `DeviceSdk` is a thread-safe singleton. `init()` and `collect()` can be called from any thread.
- The coroutine-based `collect()` runs modules on `Dispatchers.Default`.
- The callback-based `collect()` invokes the callback on a background thread -- post to the main thread if updating UI.
- Identity values (`deviceId`, `appGuid`, `firstSeen`) use double-checked locking to prevent duplicate generation under contention.
- `ModuleCache` uses `ConcurrentHashMap` for thread-safe static module caching.

---

## Architecture Overview

```
DeviceSdk (public API)
  |
  +-- SdkConfig (configuration)
  +-- ModuleOrchestrator (parallel execution, timeouts)
  |     |
  |     +-- SignalModule interface
  |     |     +-- AppModule, HardwareModule, ScreenModule, ...
  |     |
  |     +-- ModuleCache (static module caching, Play Integrity TTL)
  |
  +-- IdentityStore (encrypted persistent IDs)
  +-- SealedBoxCrypto (NaCl sealed box: X25519 + XSalsa20-Poly1305)
  +-- DeviceSessionBuilder (structured session from raw signals)
  +-- JsonPayloadBuilder (JSON serialization)
```

**Encryption:** `buildDeviceSession()` uses NaCl sealed box encryption (X25519 key agreement, HSalsa20 key derivation, XSalsa20-Poly1305 authenticated encryption). The implementation uses Bouncy Castle and is compatible with libsodium's `crypto_box_seal`.
