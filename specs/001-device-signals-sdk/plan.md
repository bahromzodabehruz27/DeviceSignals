# Implementation Plan: Device Signals SDK

**Branch**: `001-device-signals-sdk` | **Date**: 2026-09-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-device-signals-sdk/spec.md`

## Summary

Build an Android AAR library (`device-signals-sdk`) that collects anti-fraud device signals from 12 modular collectors, returning a JSON payload on demand. The SDK uses a singleton `DeviceSdk` entry point with Kotlin coroutines internally and a callback variant for Java. Each module runs in its own coroutine with independent timeout and graceful degradation. A demo Compose app (`app` module) exercises the SDK with module checkboxes and JSON output display.

## Technical Context

**Language/Version**: Kotlin 2.2.10 (existing project), Java 11 target compatibility

**Primary Dependencies**:
- kotlinx-coroutines-core (concurrent module execution)
- androidx.security:security-crypto (EncryptedSharedPreferences for device_id/app_guid)
- com.google.android.play:integrity (optional, compileOnly — Play Integrity token)
- androidx.lifecycle:lifecycle-common (Activity auto-detach observer)

**Storage**: EncryptedSharedPreferences (device_id, app_guid persistence)

**Testing**: JUnit 4 (unit), AndroidX Test / Espresso (instrumented), Robolectric (optional for module unit tests)

**Target Platform**: Android 7–16 (minSdk 24, compileSdk 37), ARM64 + x86_64

**Project Type**: Android AAR library + demo application

**Performance Goals**:
- Full collection (11 modules, excl. PLAY_INTEGRITY): < 500 ms on Snapdragon 6xx
- Partial collection (SCREEN + NETWORK): < 50 ms
- `init()`: < 20 ms, no main-thread disk I/O

**Constraints**:
- AAR < 500 KB (< 800 KB with Play Integrity), DEX methods < 3,000
- Dependencies limited to coroutines, security-crypto, play-integrity (compileOnly)
- No dangerous permissions declared in SDK manifest
- Manual JSON serialization (JSONObject/StringBuilder, no Gson/Moshi/kotlinx.serialization)
- R8 obfuscation with consumer ProGuard rules

**Scale/Scope**: Single library consumed by host Android apps; 12 signal modules, ~100 signal fields total

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution file is an unpopulated template — no project principles or governance constraints are defined. No gates to enforce. Proceeding.

## Project Structure

### Documentation (this feature)

```text
specs/001-device-signals-sdk/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── public-api.kt   # Public API surface contract
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
sdk/                              # AAR library module
├── build.gradle.kts
├── consumer-rules.pro            # Consumer ProGuard rules
├── proguard-rules.pro            # SDK obfuscation rules
└── src/
    ├── main/
    │   ├── AndroidManifest.xml   # INTERNET + ACCESS_NETWORK_STATE only
    │   └── java/tj/behruz/devicesignals/sdk/
    │       ├── DeviceSdk.kt              # Singleton entry point (init, collect, attach, detach)
    │       ├── SdkConfig.kt              # Configuration data class
    │       ├── CollectResult.kt           # Result with status, data, missing_fields, toJson()
    │       ├── Module.kt                  # Enum of 12 modules
    │       ├── DeviceSdkException.kt      # Exception hierarchy
    │       ├── SdkLogger.kt               # Logger interface + observability callback
    │       ├── internal/
    │       │   ├── ModuleOrchestrator.kt  # Concurrent module dispatch + timeout
    │       │   ├── SignalModule.kt        # Module interface
    │       │   ├── ModuleResult.kt        # Per-module result
    │       │   ├── JsonPayloadBuilder.kt  # Manual JSON serialization
    │       │   ├── IdentityStore.kt       # EncryptedSharedPreferences for device_id/app_guid
    │       │   ├── SignatureStore.kt      # Encoded detection signatures (NFR-010)
    │       │   └── modules/
    │       │       ├── AppModule.kt
    │       │       ├── HardwareModule.kt
    │       │       ├── ScreenModule.kt
    │       │       ├── IntegrityModule.kt
    │       │       ├── PlayIntegrityModule.kt
    │       │       ├── DebugModule.kt
    │       │       ├── ThreatsModule.kt
    │       │       ├── TelephonyModule.kt
    │       │       ├── NetworkModule.kt
    │       │       ├── LocationModule.kt
    │       │       ├── PowerModule.kt
    │       │       └── SystemModule.kt
    │       └── internal/cache/
    │           └── ModuleCache.kt         # Static module cache + Play Integrity TTL cache
    └── test/                              # Unit tests (JVM)
        └── java/tj/behruz/devicesignals/sdk/
            ├── CollectResultTest.kt
            ├── ModuleOrchestratorTest.kt
            ├── JsonPayloadBuilderTest.kt
            └── modules/                   # Per-module unit tests

app/                              # Demo application (existing module)
├── build.gradle.kts              # Add sdk dependency
└── src/main/java/tj/behruz/devicesignals/
    ├── MainActivity.kt           # Compose UI: module checkboxes + JSON display
    └── ui/theme/                  # Existing theme files
```

**Structure Decision**: Two-module Gradle project. The `sdk` module produces the AAR artifact; the `app` module is the demo application that depends on `sdk`. This cleanly separates the distributable library from the demo, and the existing `app` module is repurposed as the demo.

## Complexity Tracking

No constitution violations to justify.
