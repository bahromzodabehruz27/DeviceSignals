# Feature Specification: Device Signals SDK

**Feature Branch**: `001-device-signals-sdk`

**Created**: 2026-09-19

**Status**: Draft

**Input**: Android AAR library for anti-fraud device signal collection with modular architecture, returning JSON payload on demand.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Full Device Signal Collection (Priority: P1)

As a host application developer, I want to initialize the SDK once and then collect a complete set of device signals on demand, so that my backend can perform anti-fraud analysis using comprehensive device data.

**Why this priority**: This is the core value proposition of the SDK — collecting all available device signals in a single call. Without this, the SDK has no purpose.

**Independent Test**: Can be fully tested by calling `DeviceSdk.init()` followed by `DeviceSdk.collect(sessionId, visitorId)` and verifying the returned JSON contains all 12 module sections with valid data.

**Acceptance Scenarios**:

1. **Given** the SDK is initialized with default config, **When** `collect()` is called with valid sessionId and visitorId, **Then** a `CollectResult` is returned with status `SUCCESS`, all module data populated, and `missing_fields` is empty.
2. **Given** the SDK is initialized, **When** `collect()` is called with an empty sessionId, **Then** a `DeviceSdkException.InvalidArgumentException` is thrown and no data collection occurs.
3. **Given** the SDK is initialized, **When** `collect()` is called with a visitorId exceeding 128 characters, **Then** a `DeviceSdkException.InvalidArgumentException` is thrown.
4. **Given** the SDK is NOT initialized, **When** `collect()` is called, **Then** a `DeviceSdkException.NotInitializedException` is thrown with a descriptive message.

---

### User Story 2 - Selective Module Collection (Priority: P1)

As a host application developer, I want to collect only specific signal modules (e.g., screen and network only), so that I can minimize collection time and resource usage for scenarios where I only need partial data.

**Why this priority**: Modularity is the key differentiator of this SDK. Host apps must be able to request exactly the data they need without overhead from unused modules.

**Independent Test**: Can be tested by calling `collect()` with `modules = setOf(Module.SCREEN, Module.NETWORK)` and verifying only `screen` and `network` objects appear in the JSON output.

**Acceptance Scenarios**:

1. **Given** the SDK is initialized, **When** `collect()` is called with `modules = setOf(Module.SCREEN, Module.NETWORK)`, **Then** the returned JSON contains only `screen` and `network` module objects plus metadata, and completes within 50 ms.
2. **Given** the SDK config has `enabledModules` that excludes `NETWORK`, **When** `collect()` is called with `modules = setOf(Module.SCREEN, Module.NETWORK)`, **Then** only `screen` data is returned (intersection of config and request).
3. **Given** the SDK is initialized, **When** `collect()` is called with `modules = Modules.ALL - Module.PLAY_INTEGRITY`, **Then** all modules except `play_integrity` are collected and `play_integrity` key is absent from JSON.

---

### User Story 3 - Graceful Degradation on Missing Permissions (Priority: P1)

As a host application developer, I want the SDK to gracefully handle missing permissions and partial failures, so that my app never crashes due to the SDK and I always get as much data as possible.

**Why this priority**: An anti-fraud SDK must never be the cause of app instability. Graceful degradation is essential for production reliability.

**Independent Test**: Can be tested by revoking `READ_PHONE_STATE` permission and calling `collect()` — verifying that telephony fields appear as `null` in the JSON, listed in `missing_fields`, and status is `PARTIAL`.

**Acceptance Scenarios**:

1. **Given** `READ_PHONE_STATE` permission is not granted, **When** the TELEPHONY module is collected, **Then** permission-dependent fields are `null`, listed in `missing_fields` as `telephony.<field>`, and status is `PARTIAL`.
2. **Given** a module times out (exceeds `moduleTimeoutMs`), **When** other modules have completed, **Then** the timed-out module's fields are in `missing_fields` and remaining modules return their data normally.
3. **Given** a module throws an unexpected exception, **When** `collect()` is running, **Then** other modules are unaffected, the failed module's fields appear in `missing_fields`, and status is `PARTIAL`.
4. **Given** the overall `timeoutMs` is exceeded before any module completes, **When** `collect()` returns, **Then** status is `FAILED`.

---

### User Story 4 - Java Interoperability (Priority: P2)

As a Java-based host application developer, I want to use the SDK via callbacks instead of Kotlin coroutines, so that I can integrate the SDK without adopting Kotlin coroutines in my project.

**Why this priority**: Many Android apps still use Java. Callback-based API ensures broad adoption.

**Independent Test**: Can be tested by calling the Java callback variant of `collect()` from a Java class and verifying the callback receives the `CollectResult`.

**Acceptance Scenarios**:

1. **Given** a Java host application, **When** `DeviceSdk.collect(sessionId, visitorId, callback)` is called, **Then** the callback receives the `CollectResult` on a background thread.
2. **Given** a Java host application, **When** `DeviceSdk.init()` is called via `@JvmStatic`, **Then** initialization succeeds identically to the Kotlin variant.

---

### User Story 5 - Security Signal Detection (Priority: P2)

As a fraud analyst (via the host app), I want the SDK to detect rooted devices, emulators, debuggers, runtime tampering, overlays, and remote control, so that the backend can flag high-risk sessions.

**Why this priority**: Security signals are the primary anti-fraud value. They require specialized detection logic but depend on the core collection framework from P1.

**Independent Test**: Can be tested on a rooted device (Magisk) verifying `is_rooted = true`, on an emulator verifying `is_emulator = true`, and with Frida attached verifying `is_runtime_tampering_detected = true`.

**Acceptance Scenarios**:

1. **Given** a rooted device with Magisk, **When** INTEGRITY module is collected, **Then** `is_rooted` is `true` and `bootloader_state` is `UNLOCKED`.
2. **Given** an Android Studio AVD emulator, **When** HARDWARE module is collected, **Then** `is_emulator` is `true`.
3. **Given** Frida is attached to the process, **When** DEBUG module is collected, **Then** `is_runtime_tampering_detected` is `true`.
4. **Given** an overlay app is active, **When** THREATS module is collected with an attached Activity, **Then** `is_overlay_detected` is `true`.
5. **Given** no Activity is attached via `DeviceSdk.attach()`, **When** THREATS module is collected, **Then** overlay and screen-capture fields are in `missing_fields`.

---

### User Story 6 - Play Integrity Token (Priority: P3)

As a host application developer, I want the SDK to optionally obtain a Play Integrity token, so that my backend can verify device integrity through Google's attestation service.

**Why this priority**: Play Integrity is a valuable but optional signal. It requires Google Play Services and adds latency, so it's a lower priority than core signals.

**Independent Test**: Can be tested by configuring `playIntegrityCloudProjectNumber` and verifying a token is returned; and by omitting the config and verifying the module is excluded.

**Acceptance Scenarios**:

1. **Given** `playIntegrityCloudProjectNumber` is configured, **When** PLAY_INTEGRITY module is collected, **Then** a non-empty `token` string is returned.
2. **Given** `playIntegrityCloudProjectNumber` is NOT configured, **When** full collection runs, **Then** `play_integrity` module is automatically excluded.
3. **Given** Google Play Services are not available (e.g., Huawei device), **When** PLAY_INTEGRITY module is requested, **Then** the module fails gracefully with fields in `missing_fields`.
4. **Given** a Play Integrity token was obtained less than 5 minutes ago, **When** `collect()` is called again, **Then** the cached token is returned without a new network request.

---

### User Story 7 - Demo Application (Priority: P3)

As a developer evaluating the SDK, I want a demo application that lets me select modules via checkboxes and view the raw JSON result, so that I can quickly verify integration and understand the output format.

**Why this priority**: The demo app accelerates evaluation and testing but is not part of the SDK itself.

**Independent Test**: Can be tested by installing the demo APK, selecting specific modules, tapping "Collect", and verifying the displayed JSON matches the expected schema.

**Acceptance Scenarios**:

1. **Given** the demo app is installed, **When** the user selects SCREEN and NETWORK checkboxes and taps "Collect", **Then** the JSON result shows only `screen` and `network` modules.
2. **Given** the demo app is installed, **When** the user taps "Collect" with all modules selected, **Then** the full JSON payload is displayed and is valid against the JSON Schema.

---

### Edge Cases

- What happens when `collect()` is called concurrently from multiple threads? Each call must be independent and not interfere with others.
- What happens on Android Auto, TV, or Wear OS? The SDK must not crash, though some signals may be unavailable.
- What happens in a multi-process application? `device_id` and `app_guid` must remain consistent across processes via EncryptedSharedPreferences.
- What happens when the device has no network connectivity? Network-dependent modules (PLAY_INTEGRITY) fail gracefully; non-network modules collect normally.
- What happens when `collect()` is called immediately after `init()`? Init must complete within 20 ms and not block collect.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: SDK MUST provide a singleton `DeviceSdk` with `init(context, config)` and `collect(sessionId, visitorId, modules?)` methods.
- **FR-002**: `collect()` MUST be a suspend function (Kotlin) with a callback variant for Java interoperability.
- **FR-003**: `collect()` MUST never execute on the main thread; all work runs on `Dispatchers.Default/IO`.
- **FR-004**: `sessionId` and `visitorId` MUST be required non-empty strings up to 128 characters; empty or null values MUST throw `DeviceSdkException.InvalidArgumentException` before any collection starts.
- **FR-005**: SDK MUST support 12 signal modules: APP, HARDWARE, SCREEN, INTEGRITY, PLAY_INTEGRITY, DEBUG, THREATS, TELEPHONY, NETWORK, LOCATION, POWER, SYSTEM.
- **FR-006**: Each module MUST implement a `SignalModule` interface and execute independently in its own coroutine with a per-module timeout.
- **FR-007**: The effective module set MUST be the intersection of `config.enabledModules` and the `modules` parameter passed to `collect()`.
- **FR-008**: Unrequested modules MUST be completely absent from the JSON output (no null, no empty object).
- **FR-009**: Fields that could not be collected MUST appear as `null` in their module object and be listed in `collection.missing_fields` as `"<module>.<field>"`.
- **FR-010**: `collect()` MUST return a `CollectResult` with status `SUCCESS` (all fields collected), `PARTIAL` (some fields missing), or `FAILED` (no modules collected or global timeout exceeded).
- **FR-011**: `toJson()` MUST produce a JSON string conforming to schema version `1.0` with all metadata fields (`schema_version`, `id`, `visitor_id`, `collected_at`, `platform`, `collection`).
- **FR-012**: Static modules (APP, HARDWARE, SCREEN) MAY be cached for the process lifetime; dynamic modules MUST be collected fresh on each call.
- **FR-013**: PLAY_INTEGRITY module MUST be automatically excluded when `playIntegrityCloudProjectNumber` is not configured. Its token MUST be cached for 5 minutes in process memory as a single shared cache entry (not per-session), since the token attests to the device rather than the session.
- **FR-014**: THREATS module MUST require `DeviceSdk.attach(activity)` for overlay and screen-capture signals; without it, those fields go to `missing_fields`. The SDK MUST auto-detach via an `Activity.lifecycle` observer when the Activity is destroyed to prevent memory leaks. An optional `DeviceSdk.detach()` method MAY be called for early cleanup.
- **FR-015**: SDK MUST generate and persist `device_id` (UUID v7) and `app_guid` (UUID) in EncryptedSharedPreferences. Both identifiers regenerate naturally when app data is cleared (standard SharedPreferences lifecycle); no secondary storage fallback is used.
- **FR-016**: SDK MUST provide `SdkLogger` interface for custom logging and optional `onModuleCompleted(module, durationMs, status)` callback for observability.
- **FR-017**: Calling `collect()` before `init()` MUST throw `DeviceSdkException.NotInitializedException` with a descriptive message.
- **FR-018**: Multiple concurrent `collect()` calls MUST be independent and not interfere with each other.

### Non-Functional Requirements

- **NFR-001**: Full collection (without PLAY_INTEGRITY) MUST complete within 500 ms on a mid-range device (Snapdragon 6xx, Android 12).
- **NFR-002**: Partial collection of SCREEN + NETWORK MUST complete within 50 ms.
- **NFR-003**: Default global timeout MUST be 3,000 ms; default per-module timeout MUST be 1,500 ms.
- **NFR-004**: `init()` MUST complete within 20 ms with no disk I/O on the main thread.
- **NFR-005**: AAR size MUST be under 500 KB without Play Integrity, under 800 KB with it. DEX method count increase MUST be under 3,000.
- **NFR-006**: SDK MUST NOT declare dangerous permissions in its manifest (only INTERNET and ACCESS_NETWORK_STATE). Optional permissions are the host app's responsibility.
- **NFR-007**: SDK MUST NOT collect IMEI, serial number, MAC address, app names list, contacts, or precise GPS coordinates.
- **NFR-008**: SSID and BSSID MUST only be transmitted as SHA-256 hashes.
- **NFR-009**: SDK MUST be obfuscated via R8 with consumer ProGuard rules preserving the public API.
- **NFR-010**: Detection signatures (su paths, Frida library names, RAT packages) MUST be stored encoded/encrypted and decoded at runtime.
- **NFR-011**: Release-build logs MUST NOT contain signal values — only module names and statuses.
- **NFR-012**: SDK MUST support minSdk 24, Kotlin 2.x, and be compatible with Android 7 through 16.
- **NFR-013**: Dependencies MUST be limited to kotlinx-coroutines, androidx.security:security-crypto, and com.google.android.play:integrity (optional, compileOnly).
- **NFR-014**: SDK MUST NOT crash on Android Auto, TV, or Wear OS platforms.

### Key Entities

- **CollectResult**: The outcome of a `collect()` call — contains status, module data map, missing fields list, duration, and provides `toJson()` serialization.
- **Module**: Enumeration of the 12 signal categories (APP, HARDWARE, SCREEN, etc.), each with a JSON key and static/dynamic classification.
- **SignalModule**: Interface that each module implements — defines the module name and a suspend `collect()` method returning `ModuleResult`.
- **ModuleResult**: Per-module outcome containing the signal data map and a list of fields that could not be collected.
- **SdkConfig**: Configuration object holding timeouts, Play Integrity project number, log level, and enabled modules whitelist.
- **Payload**: The top-level JSON structure containing schema version, session metadata, collection summary, and per-module signal objects.
- **DeviceSdkException**: Custom base exception class for all SDK errors. Subtypes: `NotInitializedException` (thrown when `collect()` is called before `init()`), `InvalidArgumentException` (thrown for invalid `sessionId`/`visitorId` inputs).

### Module Field Definitions

Each module's JSON key, fields, types, and collection notes are defined below. Fields requiring optional permissions degrade to `null` when the permission is not granted (listed in `missing_fields`). All string fields have a maximum length of 256 characters unless noted otherwise.

#### APP (JSON key: `app`, static)
| Field | Type | Description |
|-------|------|-------------|
| `app_name` | string | Application label from PackageManager |
| `package_name` | string | Application package name |
| `version_name` | string | `versionName` from PackageInfo |
| `version_code` | long | `longVersionCode` from PackageInfo |
| `install_source` | string\|null | Installer package name (e.g., `com.android.vending`) |
| `first_install_time` | long | Epoch millis of first install |
| `last_update_time` | long | Epoch millis of last update |
| `is_system_app` | boolean | Whether the app has `FLAG_SYSTEM` |
| `app_guid` | string | Persistent per-app UUID from EncryptedSharedPreferences |

#### HARDWARE (JSON key: `hardware`, static)
| Field | Type | Description |
|-------|------|-------------|
| `manufacturer` | string | `Build.MANUFACTURER` |
| `brand` | string | `Build.BRAND` |
| `model` | string | `Build.MODEL` |
| `device` | string | `Build.DEVICE` |
| `board` | string | `Build.BOARD` |
| `hardware` | string | `Build.HARDWARE` |
| `soc_manufacturer` | string\|null | SoC manufacturer (API 31+, null below) |
| `soc_model` | string\|null | SoC model (API 31+, null below) |
| `total_ram_mb` | long | Total RAM in megabytes |
| `total_storage_mb` | long | Total internal storage in megabytes |
| `cpu_cores` | int | Number of available processor cores |
| `cpu_architecture` | string | Primary CPU ABI (e.g., `arm64-v8a`) |
| `is_emulator` | boolean | Heuristic emulator detection result |

#### SCREEN (JSON key: `screen`, static)
| Field | Type | Description |
|-------|------|-------------|
| `width_px` | int | Screen width in pixels |
| `height_px` | int | Screen height in pixels |
| `density_dpi` | int | Screen density in DPI |
| `density_bucket` | string | Density qualifier (`mdpi`, `hdpi`, `xxhdpi`, etc.) |
| `refresh_rate_hz` | float | Display refresh rate in Hz |
| `orientation` | string | Current orientation (`portrait` or `landscape`) |
| `font_scale` | float | User's font scale setting |

#### INTEGRITY (JSON key: `integrity`, dynamic)
| Field | Type | Description |
|-------|------|-------------|
| `is_rooted` | boolean | Root detection result (su binaries, Magisk, etc.) |
| `root_indicators` | string[] | List of matched root indicators (encoded at rest per NFR-010) |
| `bootloader_state` | string | `LOCKED`, `UNLOCKED`, or `UNKNOWN` |
| `se_linux_status` | string | `ENFORCING`, `PERMISSIVE`, or `DISABLED` |
| `is_verified_boot` | boolean | Whether verified boot is active |

#### PLAY_INTEGRITY (JSON key: `play_integrity`, dynamic)
| Field | Type | Description |
|-------|------|-------------|
| `token` | string\|null | Play Integrity API token; cached 5 min in-process (FR-013) |

#### DEBUG (JSON key: `debug`, dynamic)
| Field | Type | Description |
|-------|------|-------------|
| `is_debuggable` | boolean | Whether `ApplicationInfo.FLAG_DEBUGGABLE` is set |
| `is_debugger_attached` | boolean | `Debug.isDebuggerConnected()` result |
| `is_usb_debugging_enabled` | boolean | ADB / USB debugging setting |
| `is_runtime_tampering_detected` | boolean | Frida, Xposed, or similar runtime hooks detected |
| `tampering_indicators` | string[] | Matched tampering signatures (encoded at rest per NFR-010) |

#### THREATS (JSON key: `threats`, dynamic)
| Field | Type | Description |
|-------|------|-------------|
| `installed_threat_apps` | string[] | Package names of known RAT/remote-control apps detected (encoded at rest per NFR-010) |
| `is_overlay_detected` | boolean\|null | Overlay window detected; `null` if no Activity attached (FR-014) |
| `is_screen_being_captured` | boolean\|null | Screen capture/recording active; `null` if no Activity attached (FR-014) |
| `is_accessibility_service_active` | boolean | Any accessibility service enabled on device |
| `accessibility_services` | string[] | List of enabled accessibility service component names |
| `is_remote_control_detected` | boolean | Known remote-control apps installed and running |

#### TELEPHONY (JSON key: `telephony`, dynamic)
Requires `READ_PHONE_STATE` permission for all fields except `phone_type`.
| Field | Type | Description |
|-------|------|-------------|
| `sim_operator` | string\|null | SIM operator numeric (MCC+MNC) |
| `sim_country_iso` | string\|null | SIM country ISO code |
| `network_operator` | string\|null | Network operator numeric (MCC+MNC) |
| `network_country_iso` | string\|null | Network country ISO code |
| `phone_type` | string | `NONE`, `GSM`, `CDMA`, or `SIP` |
| `sim_state` | string | `READY`, `ABSENT`, `UNKNOWN`, etc. |
| `is_multi_sim` | boolean\|null | Whether device supports dual SIM (API 29+) |
| `active_sim_count` | int\|null | Number of active SIM subscriptions |

#### NETWORK (JSON key: `network`, dynamic)
| Field | Type | Description |
|-------|------|-------------|
| `connection_type` | string | `WIFI`, `CELLULAR`, `ETHERNET`, `VPN`, `NONE`, or `UNKNOWN` |
| `is_vpn_active` | boolean | Whether a VPN network is active |
| `vpn_interfaces` | string[] | Network interface names with VPN capability |
| `wifi_ssid_hash` | string\|null | SHA-256 hash of SSID (NFR-008); requires `ACCESS_FINE_LOCATION` + `ACCESS_WIFI_STATE` |
| `wifi_bssid_hash` | string\|null | SHA-256 hash of BSSID (NFR-008); same permissions as SSID |
| `wifi_frequency_mhz` | int\|null | Wi-Fi frequency in MHz |
| `proxy_host` | string\|null | Configured HTTP proxy host |
| `proxy_port` | int\|null | Configured HTTP proxy port |
| `is_proxy_configured` | boolean | Whether an HTTP proxy is set |

#### LOCATION (JSON key: `location`, dynamic)
Does NOT collect precise GPS coordinates (NFR-007). Requires `ACCESS_COARSE_LOCATION` for coordinate fields.
| Field | Type | Description |
|-------|------|-------------|
| `is_location_enabled` | boolean | Whether location services are enabled |
| `available_providers` | string[] | Active location providers (`gps`, `network`, `passive`) |
| `latitude_coarse` | double\|null | Coarse latitude (network provider, ~city-level accuracy) |
| `longitude_coarse` | double\|null | Coarse longitude (network provider, ~city-level accuracy) |
| `accuracy_m` | float\|null | Accuracy radius in meters |
| `timezone_id` | string | IANA timezone identifier (e.g., `Asia/Dushanbe`) |
| `timezone_offset_minutes` | int | UTC offset in minutes |

#### POWER (JSON key: `power`, dynamic)
| Field | Type | Description |
|-------|------|-------------|
| `battery_level_pct` | int | Battery level as 0–100 percentage |
| `is_charging` | boolean | Whether device is currently charging |
| `charging_type` | string | `USB`, `AC`, `WIRELESS`, or `NONE` |
| `battery_health` | string | `GOOD`, `OVERHEAT`, `DEAD`, `UNKNOWN`, etc. |
| `is_power_save_mode` | boolean | Whether battery saver is active |
| `is_battery_present` | boolean | Whether a battery is present (false on some TV/Auto) |

#### SYSTEM (JSON key: `system`, dynamic)
| Field | Type | Description |
|-------|------|-------------|
| `os_version` | string | `Build.VERSION.RELEASE` (e.g., `14`) |
| `api_level` | int | `Build.VERSION.SDK_INT` |
| `build_fingerprint` | string | `Build.FINGERPRINT` |
| `build_tags` | string | `Build.TAGS` (e.g., `release-keys`) |
| `build_type` | string | `Build.TYPE` (e.g., `user`, `userdebug`) |
| `security_patch_level` | string | `Build.VERSION.SECURITY_PATCH` |
| `device_id` | string | Persistent device UUID v7 from EncryptedSharedPreferences (FR-015) |
| `language` | string | Device locale language tag (e.g., `en-US`) |
| `uptime_ms` | long | System uptime since boot in milliseconds |
| `elapsed_realtime_ms` | long | Elapsed realtime since boot including deep sleep |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Full signal collection returns all fields from all 12 modules on a reference device with status `SUCCESS` and zero `missing_fields`.
- **SC-002**: Selective collection of 2 modules (SCREEN + NETWORK) completes within 50 ms and returns only those modules in the JSON.
- **SC-003**: Excluding a module from full collection results in its complete absence from the JSON output.
- **SC-004**: A module timing out or crashing does not affect other modules; the overall result is `PARTIAL` with the failed fields in `missing_fields`.
- **SC-005**: Missing optional permissions never cause exceptions, dialogs, or crashes — affected fields appear as `null` with `PARTIAL` status.
- **SC-006**: All 9 test environments in the validation matrix produce their expected signal values (emulator detection, root detection, VPN detection, etc.).
- **SC-007**: Generated JSON passes validation against the published JSON Schema for every collection scenario.
- **SC-008**: AAR artifact size stays under 500 KB (800 KB with Play Integrity) and `init()` completes within 20 ms.
- **SC-009**: Demo application allows module selection via checkboxes and displays the resulting JSON payload.
- **SC-010**: Integration documentation enables a developer to produce their first payload in 10 lines of code or fewer without contacting SDK developers.

## Clarifications

### Session 2026-09-19

- Q: Should the spec define the exact signal fields each of the 12 modules collects, or defer to planning? → A: Define all module fields in the spec now.
- Q: When should the persistent `device_id` (UUID v7) be regenerated? → A: Regenerate when app data is cleared (natural SharedPreferences behavior).
- Q: Should `DeviceSdk.attach(activity)` auto-detach when the Activity is destroyed? → A: Yes, auto-detach via Lifecycle observer on Activity destroy.
- Q: Should the Play Integrity token cache be shared across all `collect()` calls regardless of `sessionId`? → A: Yes, shared process-level cache with 5-min TTL.
- Q: Should the SDK define a custom exception hierarchy or use standard Java exceptions? → A: Custom base `DeviceSdkException` with subtypes (`NotInitializedException`, `InvalidArgumentException`).

## Assumptions

- Host applications target Android (minSdk 24+) and are built with Gradle.
- Host applications are responsible for declaring and requesting optional permissions (READ_PHONE_STATE, ACCESS_COARSE_LOCATION) before calling `collect()`.
- The SDK does not send data to any server; the host application handles network transmission of the JSON payload.
- `device_id` and `app_guid` persistence relies on EncryptedSharedPreferences, which requires API 23+ (satisfied by minSdk 24).
- Play Integrity API availability depends on Google Play Services; the SDK degrades gracefully on GMS-less devices.
- The server-side component (token decryption, IP extraction, first_seen/last_seen tracking) is out of scope.
- iOS version and cross-platform abstraction are out of scope.
- Threat detection signatures (Frida, RAT packages, overlay apps) are maintained as an internal list and updated with SDK releases — no over-the-air signature updates.
- JSON serialization uses manual StringBuilder/JSONObject construction to avoid additional library dependencies (no Gson, Moshi, or kotlinx.serialization).