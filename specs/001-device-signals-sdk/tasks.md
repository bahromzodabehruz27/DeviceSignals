# Tasks: Device Signals SDK

**Input**: Design documents from `/specs/001-device-signals-sdk/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/public-api.kt, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the `sdk` Gradle module and configure build tooling

- [x] T001 Create `sdk` Android library module with `build.gradle.kts` at `sdk/build.gradle.kts` — apply `com.android.library` and `org.jetbrains.kotlin.android` plugins, set `namespace = "tj.behruz.devicesignals.sdk"`, `minSdk = 24`, `compileSdk = 37`, Java 11 compatibility. Add dependencies: `kotlinx-coroutines-core`, `androidx.security:security-crypto`, `androidx.lifecycle:lifecycle-common`. Add `compileOnly` dependency on `com.google.android.play:integrity`. Enable R8 for release builds.
- [x] T002 Register `sdk` module in `settings.gradle.kts` — add `include(":sdk")` and add `android-library` plugin alias to root `build.gradle.kts`
- [x] T003 [P] Create SDK manifest at `sdk/src/main/AndroidManifest.xml` — declare only `INTERNET` and `ACCESS_NETWORK_STATE` permissions (NFR-006). No activities, services, or receivers.
- [x] T004 [P] Create consumer ProGuard rules at `sdk/consumer-rules.pro` — keep all public API classes in `tj.behruz.devicesignals.sdk.*` (DeviceSdk, SdkConfig, CollectResult, Module, CollectStatus, ModuleStatus, LogLevel, SdkLogger, CollectCallback, DeviceSdkException and subtypes). Obfuscate `internal` package.
- [x] T005 [P] Create SDK ProGuard rules at `sdk/proguard-rules.pro` — R8 obfuscation config for release builds, keep rules for EncryptedSharedPreferences and Play Integrity reflection.
- [x] T006 Add `sdk` dependency to demo app — update `app/build.gradle.kts` to add `implementation(project(":sdk"))`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core framework that ALL user stories depend on — public API surface, module orchestration, JSON serialization, identity persistence

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T007 Create `Module` enum at `sdk/src/main/java/tj/behruz/devicesignals/sdk/Module.kt` — 12 entries (APP, HARDWARE, SCREEN, INTEGRITY, PLAY_INTEGRITY, DEBUG, THREATS, TELEPHONY, NETWORK, LOCATION, POWER, SYSTEM) each with `jsonKey: String` and `isStatic: Boolean` properties per data-model.md. Include companion `val ALL: Set<Module> = entries.toSet()` with `@JvmField`.
- [x] T008 [P] Create `CollectStatus` enum at `sdk/src/main/java/tj/behruz/devicesignals/sdk/CollectStatus.kt` — values: SUCCESS, PARTIAL, FAILED.
- [x] T009 [P] Create `ModuleStatus` enum at `sdk/src/main/java/tj/behruz/devicesignals/sdk/ModuleStatus.kt` — values: SUCCESS, PARTIAL, FAILED, SKIPPED.
- [x] T010 [P] Create `LogLevel` enum at `sdk/src/main/java/tj/behruz/devicesignals/sdk/LogLevel.kt` — values: NONE, ERROR, WARN, DEBUG.
- [x] T011 [P] Create `SdkLogger` interface at `sdk/src/main/java/tj/behruz/devicesignals/sdk/SdkLogger.kt` — single method `fun log(level: LogLevel, tag: String, message: String)`.
- [x] T012 [P] Create `CollectCallback` functional interface at `sdk/src/main/java/tj/behruz/devicesignals/sdk/CollectCallback.kt` — `fun interface CollectCallback { fun onResult(result: CollectResult) }`.
- [x] T013 [P] Create `DeviceSdkException` hierarchy at `sdk/src/main/java/tj/behruz/devicesignals/sdk/DeviceSdkException.kt` — open class extending `RuntimeException(message, cause)` with nested `NotInitializedException(message: String = "DeviceSdk.init() must be called before collect()")` and `InvalidArgumentException(message: String)`.
- [x] T014 Create `SdkConfig` data class at `sdk/src/main/java/tj/behruz/devicesignals/sdk/SdkConfig.kt` — fields per data-model.md: `timeoutMs: Long = 3000` (> 0), `moduleTimeoutMs: Long = 1500` (> 0, ≤ timeoutMs), `enabledModules: Set<Module> = Module.ALL` (non-empty), `playIntegrityCloudProjectNumber: String? = null` (if non-null, non-blank), `logLevel: LogLevel = LogLevel.NONE`, `logger: SdkLogger? = null`, `onModuleCompleted: ((Module, Long, ModuleStatus) -> Unit)? = null`.
- [x] T015 Create `SignalModule` internal interface at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/SignalModule.kt` — property `val module: Module`, suspend method `suspend fun collect(context: Context): ModuleResult`.
- [x] T016 [P] Create `ModuleResult` internal data class at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/ModuleResult.kt` — fields: `data: Map<String, Any?>` (collected field values, null for failed fields), `missingFields: List<String>` (field names that could not be collected).
- [x] T017 Create `CollectResult` data class at `sdk/src/main/java/tj/behruz/devicesignals/sdk/CollectResult.kt` — fields per data-model.md: `status: CollectStatus`, `moduleData: Map<Module, Map<String, Any?>>`, `missingFields: List<String>` (format: `"<module>.<field>"`), `durationMs: Long`. Include `fun toJson(): String` that delegates to `JsonPayloadBuilder`. Status determination: SUCCESS if missingFields empty, PARTIAL if at least one module returned data but missingFields non-empty, FAILED if no modules completed.
- [x] T018 Create `IdentityStore` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/IdentityStore.kt` — lazy-initialize EncryptedSharedPreferences on first access (background thread, not during init per R-002). Generate and persist `device_id` as UUID v7 per R-001 (48-bit Unix timestamp millis + version bits `0111` + variant `10` + 62 SecureRandom bits, RFC 9562). Generate and persist `app_guid` as UUID v4 via `UUID.randomUUID()`. Both regenerate on app data clear (natural SharedPreferences lifecycle, no secondary storage fallback per FR-015). Use `AtomicReference` with double-checked locking for thread safety.
- [x] T019 Create `SignatureStore` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/SignatureStore.kt` — store Base64-encoded detection signatures per R-010: su paths (`/system/bin/su`, `/system/xbin/su`, etc.), Frida library names (`frida-agent`, `frida-gadget`), Xposed artifacts, known RAT package names, root management app packages. Decode at runtime on first use, cache in memory as `List<String>`. Provide accessor methods: `getSuPaths()`, `getFridaLibraries()`, `getRootAppPackages()`, `getRatPackages()`, `getXposedArtifacts()`.
- [x] T020 Create `JsonPayloadBuilder` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/JsonPayloadBuilder.kt` — build JSON using `org.json.JSONObject`/`JSONArray` per R-007. Top-level structure: `schema_version` ("1.0"), `id` (UUID v4 per call), `session_id`, `visitor_id`, `collected_at` (ISO 8601 UTC), `platform` ("android"), `collection` object (`status`, `duration_ms`, `modules_collected` array, `missing_fields` array), then one key per collected module with its field data. Absent modules produce no key (FR-008). Null field values are written as JSON null (FR-009).
- [x] T021 Create `ModuleCache` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/cache/ModuleCache.kt` — cache static module results (APP, HARDWARE, SCREEN) for process lifetime per FR-012. Cache Play Integrity token with 5-minute TTL as single shared entry (not per-session) per FR-013/R-008. Thread-safe via `ConcurrentHashMap`. Methods: `getOrCollect(module, collector): ModuleResult`, `getCachedPlayIntegrityToken(): String?`, `cachePlayIntegrityToken(token: String)`.
- [x] T022 Create `ModuleOrchestrator` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/ModuleOrchestrator.kt` — orchestrate concurrent module execution per R-009. Accept effective module set (intersection of config.enabledModules and requested modules per FR-007). Auto-exclude PLAY_INTEGRITY when `playIntegrityCloudProjectNumber` is null (FR-013). Launch each module as `async { withTimeout(moduleTimeoutMs) { module.collect(context) } }` within `withTimeout(globalTimeoutMs)` scope. Aggregate results: collect `ModuleResult` per module, catch `TimeoutCancellationException` and other exceptions per module, populate `missingFields` as `"<module>.<field>"` for failed/timed-out modules. Invoke `onModuleCompleted` callback with module, durationMs, and status. Return aggregated `CollectResult`.
- [x] T023 Create `DeviceSdk` singleton at `sdk/src/main/java/tj/behruz/devicesignals/sdk/DeviceSdk.kt` — implement per contracts/public-api.kt. `init(context, config)`: store applicationContext and config, register all 12 module implementations, complete in < 20 ms with no disk I/O (NFR-004). `collect(sessionId, visitorId, modules?)`: suspend function, validate sessionId/visitorId (non-empty, ≤ 128 chars, throw `InvalidArgumentException` per FR-004), throw `NotInitializedException` if not initialized (FR-017), delegate to ModuleOrchestrator on `Dispatchers.Default` (FR-003). `collect(sessionId, visitorId, callback)` and `collect(sessionId, visitorId, modules, callback)`: Java callback variants using `CoroutineScope(Dispatchers.Default)`. `attach(activity)`: store `WeakReference<Activity>`, register `LifecycleObserver` for auto-detach on ON_DESTROY (FR-014). `detach()`: clear reference. All methods annotated `@JvmStatic`.

**Checkpoint**: Foundation ready — module implementations can now begin

---

## Phase 3: User Story 1 — Full Device Signal Collection (Priority: P1) 🎯 MVP

**Goal**: Initialize the SDK once and collect a complete set of device signals from all 12 modules, returning a JSON payload with status SUCCESS.

**Independent Test**: Call `DeviceSdk.init()` then `DeviceSdk.collect(sessionId, visitorId)` and verify the returned JSON contains all 12 module sections with valid data, status SUCCESS, and empty missing_fields.

- [x] T024 [P] [US1] Implement `AppModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/AppModule.kt` — collect fields per spec: `app_name` (string, PackageManager label), `package_name` (string), `version_name` (string, PackageInfo.versionName), `version_code` (long, PackageInfo.longVersionCode), `install_source` (string|null, installer package), `first_install_time` (long, epoch millis), `last_update_time` (long, epoch millis), `is_system_app` (boolean, FLAG_SYSTEM), `app_guid` (string, from IdentityStore). Static module — cached for process lifetime.
- [x] T025 [P] [US1] Implement `HardwareModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/HardwareModule.kt` — collect fields per spec: `manufacturer` (Build.MANUFACTURER), `brand` (Build.BRAND), `model` (Build.MODEL), `device` (Build.DEVICE), `board` (Build.BOARD), `hardware` (Build.HARDWARE), `soc_manufacturer` (string|null, API 31+ only), `soc_model` (string|null, API 31+ only), `total_ram_mb` (long, ActivityManager.MemoryInfo), `total_storage_mb` (long, StatFs internal storage), `cpu_cores` (int, Runtime.availableProcessors), `cpu_architecture` (string, Build.SUPPORTED_ABIS[0]), `is_emulator` (boolean, multi-signal heuristic per R-003: FINGERPRINT/MODEL/HARDWARE/PRODUCT/BRAND checks + qemu_pipe, threshold ≥ 2 matches). Static module — cached for process lifetime.
- [x] T026 [P] [US1] Implement `ScreenModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/ScreenModule.kt` — collect fields per spec: `width_px` (int), `height_px` (int), `density_dpi` (int), `density_bucket` (string, mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi), `refresh_rate_hz` (float, Display.refreshRate), `orientation` (string, "portrait" or "landscape"), `font_scale` (float, Configuration.fontScale). Static module — cached for process lifetime.
- [x] T027 [P] [US1] Implement `IntegrityModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/IntegrityModule.kt` — collect fields per spec: `is_rooted` (boolean, multi-vector per R-004), `root_indicators` (string[], matched indicators from SignatureStore decoded at runtime), `bootloader_state` (string, LOCKED/UNLOCKED/UNKNOWN via `ro.boot.verifiedbootstate` or `ro.boot.flash.locked`), `se_linux_status` (string, ENFORCING/PERMISSIVE/DISABLED via `getenforce` or `/sys/fs/selinux/enforce`), `is_verified_boot` (boolean). Dynamic module.
- [x] T028 [P] [US1] Implement `DebugModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/DebugModule.kt` — collect fields per spec: `is_debuggable` (boolean, ApplicationInfo.FLAG_DEBUGGABLE), `is_debugger_attached` (boolean, Debug.isDebuggerConnected), `is_usb_debugging_enabled` (boolean, Settings.Global.ADB_ENABLED), `is_runtime_tampering_detected` (boolean, per R-005: scan /proc/self/maps for Frida libs from SignatureStore, check Xposed artifacts, check TracerPid in /proc/self/status, scan port 27042), `tampering_indicators` (string[], matched signatures from SignatureStore decoded at runtime). Dynamic module.
- [x] T029 [P] [US1] Implement `ThreatsModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/ThreatsModule.kt` — collect fields per spec: `installed_threat_apps` (string[], match installed packages against RAT list from SignatureStore), `is_overlay_detected` (boolean|null, null if no Activity attached per FR-014; detect via TYPE_APPLICATION_OVERLAY windows per R-006), `is_screen_being_captured` (boolean|null, null if no Activity; API 34+ ScreenCaptureCallback fallback MediaRouter per R-006), `is_accessibility_service_active` (boolean, AccessibilityManager.isEnabled), `accessibility_services` (string[], enabled service component names via Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), `is_remote_control_detected` (boolean, known remote-control apps installed and running). Dynamic module. Requires Activity via `DeviceSdk.attach()` for overlay/capture fields.
- [x] T030 [P] [US1] Implement `TelephonyModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/TelephonyModule.kt` — collect fields per spec: `sim_operator` (string|null, TelephonyManager), `sim_country_iso` (string|null), `network_operator` (string|null), `network_country_iso` (string|null), `phone_type` (string, NONE/GSM/CDMA/SIP — no permission needed), `sim_state` (string, READY/ABSENT/UNKNOWN), `is_multi_sim` (boolean|null, API 29+ SubscriptionManager), `active_sim_count` (int|null). Requires READ_PHONE_STATE for all fields except phone_type — check permission and set missing fields to null with missingFields entries when not granted. Dynamic module.
- [x] T031 [P] [US1] Implement `NetworkModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/NetworkModule.kt` — collect fields per spec: `connection_type` (string, WIFI/CELLULAR/ETHERNET/VPN/NONE/UNKNOWN via ConnectivityManager), `is_vpn_active` (boolean, check NetworkCapabilities.TRANSPORT_VPN), `vpn_interfaces` (string[], network interfaces with VPN capability), `wifi_ssid_hash` (string|null, SHA-256 of SSID per NFR-008, requires ACCESS_FINE_LOCATION + ACCESS_WIFI_STATE), `wifi_bssid_hash` (string|null, SHA-256 of BSSID per NFR-008), `wifi_frequency_mhz` (int|null, WifiInfo), `proxy_host` (string|null, System.getProperty), `proxy_port` (int|null), `is_proxy_configured` (boolean). Dynamic module.
- [x] T032 [P] [US1] Implement `LocationModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/LocationModule.kt` — collect fields per spec: `is_location_enabled` (boolean, LocationManager.isProviderEnabled), `available_providers` (string[], active providers), `latitude_coarse` (double|null, coarse network provider ~city-level, requires ACCESS_COARSE_LOCATION — NO precise GPS per NFR-007), `longitude_coarse` (double|null), `accuracy_m` (float|null), `timezone_id` (string, TimeZone.getDefault().id), `timezone_offset_minutes` (int, UTC offset). Dynamic module.
- [x] T033 [P] [US1] Implement `PowerModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/PowerModule.kt` — collect fields per spec: `battery_level_pct` (int, 0–100 from BatteryManager sticky intent), `is_charging` (boolean, BATTERY_STATUS_CHARGING or BATTERY_STATUS_FULL), `charging_type` (string, USB/AC/WIRELESS/NONE from BatteryManager.EXTRA_PLUGGED), `battery_health` (string, GOOD/OVERHEAT/DEAD/UNKNOWN from EXTRA_HEALTH), `is_power_save_mode` (boolean, PowerManager.isPowerSaveMode), `is_battery_present` (boolean, EXTRA_PRESENT — false on some TV/Auto per NFR-014). Dynamic module.
- [x] T034 [P] [US1] Implement `SystemModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/SystemModule.kt` — collect fields per spec: `os_version` (string, Build.VERSION.RELEASE), `api_level` (int, Build.VERSION.SDK_INT), `build_fingerprint` (string, Build.FINGERPRINT), `build_tags` (string, Build.TAGS), `build_type` (string, Build.TYPE), `security_patch_level` (string, Build.VERSION.SECURITY_PATCH), `device_id` (string, UUID v7 from IdentityStore per FR-015), `language` (string, Locale.getDefault().toLanguageTag()), `uptime_ms` (long, SystemClock.uptimeMillis), `elapsed_realtime_ms` (long, SystemClock.elapsedRealtime). Dynamic module.
- [x] T035 [US1] Register all 12 module implementations in `DeviceSdk.init()` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/DeviceSdk.kt` — instantiate AppModule, HardwareModule, ScreenModule, IntegrityModule, PlayIntegrityModule, DebugModule, ThreatsModule, TelephonyModule, NetworkModule, LocationModule, PowerModule, SystemModule and register with ModuleOrchestrator.

**Checkpoint**: Full signal collection works — `collect()` returns all 12 modules with status SUCCESS on a device with all permissions granted

---

## Phase 4: User Story 2 — Selective Module Collection (Priority: P1)

**Goal**: Collect only specific signal modules, returning only those modules in the JSON output.

**Independent Test**: Call `collect()` with `modules = setOf(Module.SCREEN, Module.NETWORK)` and verify only `screen` and `network` objects appear in the JSON, completing within 50 ms.

- [x] T036 [US2] Verify module intersection logic in `ModuleOrchestrator` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/ModuleOrchestrator.kt` — ensure effective module set is computed as `config.enabledModules.intersect(requestedModules)` per FR-007. Unrequested modules must be completely absent from JSON output (no null, no empty object per FR-008). Verify `collection.modules_collected` array in JSON only lists collected modules.
- [x] T037 [US2] Verify JSON output exclusion in `JsonPayloadBuilder` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/JsonPayloadBuilder.kt` — ensure modules not in the effective set produce no key in the JSON output per FR-008.

**Checkpoint**: Selective collection works — requesting a subset of modules returns only those modules in JSON

---

## Phase 5: User Story 3 — Graceful Degradation on Missing Permissions (Priority: P1)

**Goal**: SDK gracefully handles missing permissions and partial failures without crashing, always returning as much data as possible.

**Independent Test**: Revoke `READ_PHONE_STATE` and call `collect()` — verify telephony fields are null, listed in missing_fields, and status is PARTIAL.

- [x] T038 [US3] Implement per-module exception handling in `ModuleOrchestrator` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/ModuleOrchestrator.kt` — wrap each module's `async` in try/catch. On `TimeoutCancellationException`: add all module fields to missingFields. On any other exception: add all module fields to missingFields, log error (module name and status only, no signal values per NFR-011). Other modules must be unaffected. Status: PARTIAL if any missingFields, FAILED if no modules completed or global timeout exceeded.
- [x] T039 [US3] Implement permission-aware field collection in each module — each module that requires optional permissions (TelephonyModule: READ_PHONE_STATE, NetworkModule: ACCESS_FINE_LOCATION + ACCESS_WIFI_STATE, LocationModule: ACCESS_COARSE_LOCATION) must check permission via `ContextCompat.checkSelfPermission()` before accessing protected APIs. On missing permission: set affected fields to null and add to ModuleResult.missingFields. Never throw, never show permission dialog (NFR-006).

**Checkpoint**: Graceful degradation works — missing permissions result in PARTIAL status with null fields, never crashes

---

## Phase 6: User Story 4 — Java Interoperability (Priority: P2)

**Goal**: Java host apps can use the SDK via callbacks instead of Kotlin coroutines.

**Independent Test**: Call `DeviceSdk.collect(sessionId, visitorId, callback)` from a Java class and verify the callback receives the CollectResult on a background thread.

- [x] T040 [US4] Verify Java-callable API surface in `DeviceSdk` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/DeviceSdk.kt` — ensure all public methods have `@JvmStatic`, callback variants accept `CollectCallback` (functional interface), callback is invoked on `Dispatchers.Default` (background thread, not main). Ensure `SdkConfig` builder pattern works from Java (data class with defaults).
- [x] T041 [US4] Verify `@JvmField` on `Module.ALL` companion property in `sdk/src/main/java/tj/behruz/devicesignals/sdk/Module.kt` — ensure Java callers can access `Module.ALL` as a field.

**Checkpoint**: Java callback API works — Java callers can init and collect without Kotlin coroutine dependencies

---

## Phase 7: User Story 5 — Security Signal Detection (Priority: P2)

**Goal**: Detect rooted devices, emulators, debuggers, runtime tampering, overlays, and remote control for anti-fraud analysis.

**Independent Test**: On a rooted device verify `is_rooted = true`, on an emulator verify `is_emulator = true`, with Frida attached verify `is_runtime_tampering_detected = true`.

- [x] T042 [US5] Enhance root detection in `IntegrityModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/IntegrityModule.kt` — implement multi-vector detection per R-004: (1) su binary check in paths from SignatureStore, (2) Magisk mount points and magiskd process detection, (3) Build.TAGS "test-keys" check, (4) writable /system partition check, (5) root management app package detection. Populate `root_indicators` with matched items. All signature strings decoded from SignatureStore at runtime (NFR-010).
- [x] T043 [P] [US5] Enhance tampering detection in `DebugModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/DebugModule.kt` — implement per R-005: (1) scan `/proc/self/maps` for Frida libraries from SignatureStore, (2) check for Xposed installer packages and `XposedBridge.jar` in classpath, (3) scan TCP port 27042 for Frida server, (4) check `/proc/self/status` TracerPid for active debugger. Populate `tampering_indicators` with matched items.
- [x] T044 [P] [US5] Enhance threat detection in `ThreatsModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/ThreatsModule.kt` — implement per R-006: overlay detection via TYPE_APPLICATION_OVERLAY window check (requires attached Activity), screen capture detection via API 34+ ScreenCaptureCallback with MediaRouter fallback, accessibility service enumeration, remote control app detection against RAT packages from SignatureStore.

**Checkpoint**: Security signals work — emulator, root, tampering, overlay, and remote control detection return correct values on test devices

---

## Phase 8: User Story 6 — Play Integrity Token (Priority: P3)

**Goal**: Optionally obtain a Play Integrity token when configured, with 5-minute caching.

**Independent Test**: Configure `playIntegrityCloudProjectNumber` and verify a token is returned; omit config and verify module is excluded.

- [x] T045 [US6] Implement `PlayIntegrityModule` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/modules/PlayIntegrityModule.kt` — use reflection to check Play Integrity API availability per R-008 (`Class.forName("com.google.android.play.core.integrity.IntegrityManagerFactory")`). If available and `playIntegrityCloudProjectNumber` is configured: request standard integrity token via `IntegrityManager.requestIntegrityToken()`. On success: return `token` string. On failure (no Play Services, network error): return null token in missingFields. Cache token in ModuleCache with 5-min TTL as shared entry (not per-session per FR-013). Dynamic module.
- [x] T046 [US6] Verify auto-exclusion logic in `ModuleOrchestrator` at `sdk/src/main/java/tj/behruz/devicesignals/sdk/internal/ModuleOrchestrator.kt` — when `playIntegrityCloudProjectNumber` is null, PLAY_INTEGRITY must be removed from the effective module set before dispatch, resulting in no `play_integrity` key in JSON.

**Checkpoint**: Play Integrity works — token returned when configured, auto-excluded when not, cached for 5 minutes

---

## Phase 9: User Story 7 — Demo Application (Priority: P3)

**Goal**: Demo app with module selection checkboxes and raw JSON result display for SDK evaluation.

**Independent Test**: Install demo APK, select specific modules, tap "Collect", verify displayed JSON matches expected schema.

- [x] T047 [US7] Redesign `MainActivity` at `app/src/main/java/tj/behruz/devicesignals/MainActivity.kt` — replace boilerplate Greeting UI with Compose layout: (1) header with SDK version, (2) two text fields for sessionId and visitorId with defaults, (3) scrollable column of 12 checkboxes (one per Module, all checked by default), (4) "Collect" button that calls `DeviceSdk.collect()` with selected modules, (5) scrollable text area showing raw JSON result with monospace font, (6) status indicator showing CollectStatus. Initialize SDK in `onCreate` with default config. Call `DeviceSdk.attach(this)` for THREATS module.
- [x] T048 [US7] Update `app/src/main/AndroidManifest.xml` — add optional permissions for full demo: `READ_PHONE_STATE`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `ACCESS_WIFI_STATE`. Add runtime permission request in MainActivity.

**Checkpoint**: Demo app works — module checkboxes, collect button, and JSON display functional

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Release hardening, logging discipline, and size validation

- [x] T049 [P] Implement release log filtering in all modules — audit every log statement across all 12 modules and internal classes to ensure release-build logs contain only module names and statuses, never signal values (NFR-011). Use `SdkLogger` interface for all SDK logging.
- [x] T050 [P] Validate AAR artifact size — build `sdk:assembleRelease`, verify AAR < 500 KB without Play Integrity, < 800 KB with it, DEX method count < 3,000 (NFR-005). If exceeding limits, audit dependencies and remove unnecessary code.
- [x] T051 [P] Validate init() performance — measure `DeviceSdk.init()` completes in < 20 ms with no main-thread disk I/O (NFR-004). Profile with Android Studio if needed.
- [x] T052 [P] Validate platform compatibility — verify SDK does not crash on Android Auto, TV, or Wear OS form factors (NFR-014). Modules that depend on unavailable hardware (battery, telephony) must degrade to null fields, not throw.
- [x] T053 Run quickstart.md validation scenarios — execute all 11 validation scenarios from `specs/001-device-signals-sdk/quickstart.md` (V-001 through V-011) on a physical device and emulator.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup (T001–T006) — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Foundational (T007–T023) — 12 module implementations
- **US2 (Phase 4)**: Depends on Foundational — verifies filtering logic already in framework
- **US3 (Phase 5)**: Depends on Foundational + at least some modules from US1 to test degradation
- **US4 (Phase 6)**: Depends on Foundational — verifies Java-callable API surface
- **US5 (Phase 7)**: Depends on US1 (T027, T028, T029) — enhances security modules
- **US6 (Phase 8)**: Depends on Foundational — PlayIntegrityModule implementation
- **US7 (Phase 9)**: Depends on US1 — needs working SDK for demo
- **Polish (Phase 10)**: Depends on all prior phases

### User Story Dependencies

- **US1 (P1)**: Depends on Foundational only — primary MVP
- **US2 (P1)**: Can start after Foundational — independent of US1 implementation
- **US3 (P1)**: Best tested after US1 modules exist, but framework logic is independent
- **US4 (P2)**: Can start after Foundational — independent of module implementations
- **US5 (P2)**: Depends on US1 modules (INTEGRITY, DEBUG, THREATS) — enhances them
- **US6 (P3)**: Can start after Foundational — independent PlayIntegrityModule
- **US7 (P3)**: Depends on US1 — needs working collect() for demo

### Within Each User Story

- Models/interfaces before services
- Services before integration
- Core implementation before refinement

### Parallel Opportunities

- **Phase 1**: T003, T004, T005 can run in parallel (different files)
- **Phase 2**: T008–T013 can run in parallel (independent enum/interface files); T015–T016 in parallel
- **Phase 3**: All 12 module implementations (T024–T034) can run in parallel (separate files, no inter-dependencies)
- **Phase 5**: US3 permission checks across modules can be parallelized
- **Phase 7**: T043, T044 can run in parallel (different module files)
- **Phase 10**: T049–T052 can run in parallel

---

## Parallel Example: User Story 1

```text
# Launch all 11 module implementations together (T024–T034 are all [P]):
Task: "Implement AppModule in sdk/.../modules/AppModule.kt"
Task: "Implement HardwareModule in sdk/.../modules/HardwareModule.kt"
Task: "Implement ScreenModule in sdk/.../modules/ScreenModule.kt"
Task: "Implement IntegrityModule in sdk/.../modules/IntegrityModule.kt"
Task: "Implement DebugModule in sdk/.../modules/DebugModule.kt"
Task: "Implement ThreatsModule in sdk/.../modules/ThreatsModule.kt"
Task: "Implement TelephonyModule in sdk/.../modules/TelephonyModule.kt"
Task: "Implement NetworkModule in sdk/.../modules/NetworkModule.kt"
Task: "Implement LocationModule in sdk/.../modules/LocationModule.kt"
Task: "Implement PowerModule in sdk/.../modules/PowerModule.kt"
Task: "Implement SystemModule in sdk/.../modules/SystemModule.kt"

# Then wire them up (T035, sequential after above):
Task: "Register all 12 modules in DeviceSdk.init()"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1 (12 module implementations)
4. **STOP and VALIDATE**: Full collect() returns all modules with SUCCESS
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Framework ready
2. Add US1 → Full collection works → **MVP!**
3. Add US2 + US3 → Module filtering + graceful degradation → **Robust MVP**
4. Add US4 → Java interop → **Broad adoption ready**
5. Add US5 → Enhanced security signals → **Anti-fraud capable**
6. Add US6 → Play Integrity → **Google attestation ready**
7. Add US7 → Demo app → **Evaluable product**
8. Polish → Release-quality artifact

### Parallel Team Strategy

With multiple developers after Foundational phase:
- Developer A: US1 modules (T024–T035)
- Developer B: US4 Java interop (T040–T041) + US6 Play Integrity (T045–T046)
- Developer C: US7 Demo app (T047–T048)
- After US1 complete: Developer A moves to US2 + US3 + US5

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- No test tasks generated — tests were not explicitly requested in the spec
- PlayIntegrityModule (T045) is listed separately from US1 because it requires optional dependency setup and is P3 priority
