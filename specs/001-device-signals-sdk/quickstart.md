# Quickstart Validation Guide: Device Signals SDK

**Date**: 2026-09-19 | **Plan**: [plan.md](plan.md)

## Prerequisites

- Android Studio with AGP 9.x+
- Physical device or emulator running Android 7+ (API 24+)
- Project cloned and on branch `001-device-signals-sdk`

## Build & Install

```bash
# Build the SDK AAR and demo app
./gradlew :sdk:assembleRelease :app:assembleDebug

# Install demo app on connected device/emulator
./gradlew :app:installDebug
```

## Validation Scenarios

### V-001: Full Collection (SC-001)

**Steps**:
1. Open the demo app
2. Ensure all 12 module checkboxes are selected
3. Tap "Collect"

**Expected**:
- JSON displayed with all 12 module sections
- `collection.status` = `"SUCCESS"`
- `collection.missing_fields` is empty (on a device with all permissions granted)
- All module fields present per [spec Module Field Definitions](spec.md#module-field-definitions)

### V-002: Selective Collection (SC-002)

**Steps**:
1. Deselect all modules except SCREEN and NETWORK
2. Tap "Collect"

**Expected**:
- JSON contains only `screen` and `network` objects (plus metadata)
- No other module keys present
- `collection.duration_ms` < 50

### V-003: Module Exclusion (SC-003)

**Steps**:
1. Deselect only PLAY_INTEGRITY, leave all others checked
2. Tap "Collect"

**Expected**:
- `play_integrity` key absent from JSON
- All other 11 modules present

### V-004: Graceful Degradation (SC-004, SC-005)

**Steps**:
1. Revoke `READ_PHONE_STATE` in device Settings → Apps → Demo → Permissions
2. Select all modules and tap "Collect"

**Expected**:
- `collection.status` = `"PARTIAL"`
- `telephony.*` permission-dependent fields are `null`
- `collection.missing_fields` contains entries like `"telephony.sim_operator"`
- App does not crash

### V-005: Uninitialized Guard (US-1, Scenario 4)

**Steps** (programmatic):
```kotlin
// Before calling init():
try {
    runBlocking { DeviceSdk.collect("s1", "v1") }
} catch (e: DeviceSdkException.NotInitializedException) {
    // Expected
}
```

**Expected**: `NotInitializedException` thrown with descriptive message.

### V-006: Invalid Arguments (US-1, Scenarios 2–3)

**Steps** (programmatic):
```kotlin
DeviceSdk.init(applicationContext)
try {
    runBlocking { DeviceSdk.collect("", "visitor1") }
} catch (e: DeviceSdkException.InvalidArgumentException) {
    // Expected: empty sessionId
}

try {
    runBlocking { DeviceSdk.collect("session1", "x".repeat(129)) }
} catch (e: DeviceSdkException.InvalidArgumentException) {
    // Expected: visitorId exceeds 128 chars
}
```

### V-007: Java Callback (US-4)

**Steps** (from a Java class):
```java
DeviceSdk.init(getApplicationContext());
DeviceSdk.collect("session1", "visitor1", result -> {
    assert result.getStatus() == CollectStatus.SUCCESS;
    String json = result.toJson();
    // Verify JSON is valid
});
```

**Expected**: Callback invoked on background thread with valid `CollectResult`.

### V-008: Emulator Detection (SC-006)

**Steps**: Run V-001 on an Android Studio AVD emulator.

**Expected**: `hardware.is_emulator` = `true`

### V-009: AAR Size (SC-008)

**Steps**:
```bash
./gradlew :sdk:assembleRelease
ls -la sdk/build/outputs/aar/sdk-release.aar
```

**Expected**: File size < 500 KB (< 800 KB if Play Integrity dependency is included).

### V-010: Init Performance (SC-008)

**Steps** (programmatic):
```kotlin
val start = System.nanoTime()
DeviceSdk.init(applicationContext)
val elapsed = (System.nanoTime() - start) / 1_000_000
assert(elapsed < 20) { "init() took ${elapsed}ms, expected < 20ms" }
```

### V-011: JSON Schema Validation (SC-007)

**Steps**: Capture the JSON output from V-001 and validate it contains:
- `schema_version` = `"1.0"`
- `id` is a valid UUID
- `collected_at` is ISO 8601
- `platform` = `"android"`
- `collection` object with `status`, `duration_ms`, `modules_collected`, `missing_fields`
- Each module object contains exactly the fields defined in the spec

## Test Execution

```bash
# Unit tests (JVM)
./gradlew :sdk:test

# Instrumented tests (requires device/emulator)
./gradlew :sdk:connectedAndroidTest

# Demo app instrumented tests
./gradlew :app:connectedAndroidTest
```
