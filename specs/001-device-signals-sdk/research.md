# Research: Device Signals SDK

**Date**: 2026-09-19 | **Plan**: [plan.md](plan.md)

## R-001: UUID v7 Generation on Android (minSdk 24)

**Decision**: Generate UUID v7 manually using `System.currentTimeMillis()` + `SecureRandom` for the random portion, formatted per RFC 9562.

**Rationale**: `java.util.UUID.randomUUID()` produces v4 only. No JDK method produces v7. Manual construction is straightforward (~15 lines): 48-bit Unix timestamp in millis → bits 48–51 = version `0111` → 2-bit variant `10` → 62 random bits from `SecureRandom`. No external library needed.

**Alternatives considered**:
- `com.fasterxml.uuid:java-uuid-generator` — adds a dependency (violates NFR-013 constraint)
- UUID v4 instead of v7 — loses temporal ordering, which is useful for backend correlation

## R-002: EncryptedSharedPreferences Initialization Without Main-Thread I/O

**Decision**: Lazy-initialize `EncryptedSharedPreferences` on first `collect()` call (background coroutine), not during `init()`. Store the instance in an `AtomicReference` with double-checked locking.

**Rationale**: `EncryptedSharedPreferences.create()` performs disk I/O (MasterKey creation + file read). NFR-004 requires `init()` to complete in < 20 ms with no main-thread disk I/O. Deferring to first collection (which already runs on `Dispatchers.IO`) satisfies both constraints.

**Alternatives considered**:
- Eager init on background thread during `init()` — adds complexity, risks race if `collect()` is called immediately
- `SharedPreferences` without encryption — violates security requirement for persistent identifiers

## R-003: Emulator Detection Heuristics

**Decision**: Multi-signal heuristic combining: `Build.FINGERPRINT` contains "generic"/"sdk"/"google_sdk", `Build.MODEL` contains "Emulator"/"Android SDK", `Build.HARDWARE` is "goldfish"/"ranchu", `Build.PRODUCT` is "sdk"/"sdk_gphone", `Build.BRAND` is "generic", and presence of `/dev/qemu_pipe` or `/dev/goldfish_pipe`.

**Rationale**: No single property is definitive. Combining 6+ signals with a threshold (≥ 2 matches → emulator) provides high accuracy across AVD, Genymotion, and cloud device farms while minimizing false positives on unusual OEM devices.

**Alternatives considered**:
- Telephony-based detection (device ID all zeros) — requires READ_PHONE_STATE permission
- CPU instruction timing — unreliable on modern emulators with hardware acceleration

## R-004: Root Detection Approach

**Decision**: Check for: (1) su binary in common paths (`/system/bin/su`, `/system/xbin/su`, etc.), (2) Magisk mount points and `magiskd` process, (3) `Build.TAGS` containing "test-keys", (4) writable `/system` partition, (5) known root management apps (com.topjohnwu.magisk, etc.). Store paths/package names encoded per NFR-010.

**Rationale**: Multi-vector approach catches both traditional root (SuperSU) and modern root (Magisk, KernelSU). Encoding signatures at rest prevents simple string analysis by adversaries.

**Alternatives considered**:
- SafetyNet/Play Integrity only — requires network, not always available
- RootBeer library — adds dependency (violates NFR-013)

## R-005: Runtime Tampering Detection (Frida, Xposed)

**Decision**: Detect via: (1) scan `/proc/self/maps` for known Frida libraries (`frida-agent`, `frida-gadget`), (2) check for Xposed installer packages and `XposedBridge.jar` in classpath, (3) scan open TCP ports (27042 default Frida port), (4) check for native hook frameworks via `/proc/self/status` TracerPid. Store library names encoded per NFR-010.

**Rationale**: `/proc/self/maps` scan is the most reliable Frida detection method. Port scanning catches remote Frida. TracerPid detects active debugger attachment. Combined approach covers major hooking frameworks.

**Alternatives considered**:
- Integrity-only approach (just check debuggable flag) — misses runtime injection
- Native (JNI) detection — increases AAR size and complexity beyond scope

## R-006: Overlay and Screen Capture Detection

**Decision**: Overlay detection via `WindowManager.getCurrentWindowMetrics()` + checking for `TYPE_APPLICATION_OVERLAY` windows (API 26+). Screen capture via `MediaProjection` callback (API 34+ `Activity.ScreenCaptureCallback`) with fallback to checking `MediaRouter` active routes on older APIs.

**Rationale**: API 34 introduced native screen capture detection callbacks. For API 24–33, fallback heuristics provide best-effort detection. Requires Activity reference (FR-014) because these APIs are window-scoped.

**Alternatives considered**:
- DRM surface detection — complex, unreliable
- Screenshot file observer — high false positive rate, requires WRITE_EXTERNAL_STORAGE

## R-007: Manual JSON Serialization Strategy

**Decision**: Use `org.json.JSONObject` and `org.json.JSONArray` (bundled with Android) for structured construction, then `toString()` for serialization. Each module's `ModuleResult` provides a `Map<String, Any?>` that `JsonPayloadBuilder` assembles into the top-level structure.

**Rationale**: `org.json` is part of the Android framework (zero-dependency). It handles escaping, null values, and nested structures correctly. `StringBuilder` alternative is faster but error-prone for escaping. Given the payload is ~2–5 KB, `JSONObject` performance is more than adequate within the 500 ms budget.

**Alternatives considered**:
- Raw `StringBuilder` — faster but risky for correctness (escaping, Unicode)
- `JsonWriter` (android.util) — streaming API, slightly lower allocation, but more verbose code

## R-008: Play Integrity API Integration (compileOnly)

**Decision**: Declare `com.google.android.play:integrity` as `compileOnly` in the SDK module. At runtime, use reflection or `try { Class.forName(...) }` to detect availability. Wrap all Play Integrity calls in a separate `PlayIntegrityModule` that gracefully returns empty `ModuleResult` when the library is absent.

**Rationale**: `compileOnly` keeps the dependency optional — host apps without Play Services won't pull in the library. Runtime class detection is the standard Android pattern for optional dependencies.

**Alternatives considered**:
- Hard `implementation` dependency — forces all consumers to include Play Services
- Separate artifact (`device-signals-sdk-play-integrity`) — adds publishing complexity for a single-class module

## R-009: Concurrent Module Execution Architecture

**Decision**: Use `coroutineScope { modules.map { async { withTimeout(moduleTimeoutMs) { it.collect(context) } } } }` pattern. Each module runs as an independent `async` with `withTimeout`. Global timeout wraps the entire scope via `withTimeout(globalTimeoutMs)`. Results are aggregated via `awaitAll()` with individual `try/catch` per deferred.

**Rationale**: Structured concurrency ensures all module coroutines are properly cancelled on global timeout. Individual `withTimeout` per module prevents one slow module from consuming the entire budget. `async` + `awaitAll` is the idiomatic Kotlin approach for fan-out/fan-in.

**Alternatives considered**:
- `Thread` + `ExecutorService` — more Java-idiomatic but doesn't integrate with caller's coroutine scope
- Sequential execution — too slow for 12 modules; misses the 500 ms target

## R-010: Signature Encoding Strategy (NFR-010)

**Decision**: Base64-encode all detection signatures (su paths, Frida library names, RAT package names) as compile-time constants. Decode at runtime into a `List<String>` on first use and cache in memory. Do not use encryption (AES) — obfuscation via encoding + R8 string obfuscation is sufficient for the threat model.

**Rationale**: Base64 prevents trivial `strings` extraction from the APK. R8 further obfuscates field/class names. Full AES encryption would require key management (the key must be in the APK anyway, providing no additional security against a determined attacker). This matches the "encoded/encrypted" language in NFR-010 at minimal complexity.

**Alternatives considered**:
- AES encryption with hardcoded key — security theater (key extractable)
- XOR with rotating key — marginally better than Base64 but more code
- Native (.so) storage — increases AAR size, out of scope
