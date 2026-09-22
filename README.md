# DeviceSignals SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bahromzodabehruz27/device-signals-sdk)](https://central.sonatype.com/artifact/io.github.bahromzodabehruz27/device-signals-sdk)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Android SDK for collecting device fingerprints, integrity signals, and fraud detection data.

---

## Installation

```kotlin
dependencies {
    implementation("io.github.bahromzodabehruz27:device-signals-sdk:0.1.0")
}
```

**Min SDK:** 24 (Android 7.0)

### Optional: Play Integrity

```kotlin
dependencies {
    implementation("com.google.android.play:integrity:1.4.0")
}
```

---

## Quick Start

```kotlin
// 1. Initialize (once, in Application.onCreate or before first use)
DeviceSdk.init(context)

// 2. Attach activity (for overlay/threat detection)
DeviceSdk.attach(activity)

// 3. Collect signals
val result = DeviceSdk.collect(
    sessionId = "your-session-id",
    visitorId = "your-visitor-id",
)

// 4. Get JSON payload
val json = result.toJson()
```

---

## Features

- **12 signal modules** — app, hardware, screen, integrity, debug, threats, telephony, network, location, power, system, Play Integrity
- **Parallel collection** — all modules run concurrently with per-module and global timeouts
- **Root & tamper detection** — su binaries, Magisk, Frida, Xposed, debugger attachment
- **Encrypted device sessions** — NaCl sealed box encryption (X25519 + XSalsa20-Poly1305), compatible with libsodium
- **Persistent identity** — encrypted device ID, app GUID, and first-seen timestamp via EncryptedSharedPreferences
- **Kotlin & Java** — coroutine and callback APIs, `@JvmStatic` for Java interop
- **Thread-safe** — concurrent access, double-checked locking, `ConcurrentHashMap` caching
- **No permissions required** — works without any runtime permissions; optional permissions unlock additional fields

---

## Modules

| Module | JSON Key | Description |
|--------|----------|-------------|
| App | `app` | Package name, version, install source, app GUID |
| Hardware | `hardware` | Manufacturer, model, RAM, CPU, emulator detection |
| Screen | `screen` | Resolution, density, refresh rate, orientation |
| Integrity | `integrity` | Root detection, bootloader state, SELinux, verified boot |
| Play Integrity | `play_integrity` | Google Play Integrity token |
| Debug | `debug` | Debugger, Frida, Xposed, USB debugging |
| Threats | `threats` | Overlay, accessibility services, RAT apps |
| Telephony | `telephony` | SIM info, carrier, phone type, multi-SIM |
| Network | `network` | Connection type, VPN, WiFi (hashed), proxy |
| Location | `location` | Coarse location, timezone, providers |
| Power | `power` | Battery level, charging state, power save |
| System | `system` | OS version, API level, uptime, language |

---

## Configuration

```kotlin
DeviceSdk.init(
    context = applicationContext,
    config = SdkConfig(
        timeoutMs = 5_000L,                    // global timeout
        moduleTimeoutMs = 2_000L,              // per-module timeout
        enabledModules = Module.ALL,           // or a subset
        logLevel = LogLevel.DEBUG,
        playIntegrityCloudProjectNumber = "123456789",
        deviceId = "your-device-id",           // for buildDeviceSession
        fraudPublicKeyBase64 = "base64-key",   // for buildDeviceSession
    ),
)
```

---

## Collecting Signals

### Kotlin (coroutines)

```kotlin
val result = DeviceSdk.collect(
    sessionId = "session-123",
    visitorId = "visitor-456",
    modules = setOf(Module.APP, Module.HARDWARE, Module.INTEGRITY),
)

when (result.status) {
    CollectStatus.SUCCESS -> // all modules collected
    CollectStatus.PARTIAL -> // some fields missing
    CollectStatus.FAILED  -> // collection failed
}
```

### Callback (Kotlin/Java)

```kotlin
DeviceSdk.collect("session-123", "visitor-456") { result ->
    val json = result.toJson()
}
```

### Java

```java
DeviceSdk.init(context, new SdkConfig());
DeviceSdk.collect("session-1", "visitor-1", result -> {
    String json = result.toJson();
});
```

---

## Encrypted Device Sessions

Build an encrypted session payload for server-side fraud analysis:

```kotlin
DeviceSdk.init(context, SdkConfig(
    deviceId = "device-123",
    fraudPublicKeyBase64 = "base64-x25519-public-key",
))

val encrypted: String = DeviceSdk.buildDeviceSession(
    sessionId = "session-123",
    visitorId = "visitor-456",
)
// Send `encrypted` (base64) to your backend
```

Decrypt on your server with the corresponding X25519 private key using libsodium's `crypto_box_seal_open`.

---

## Permissions

**No permissions are required.** The SDK works without any runtime permissions.

Optional permissions unlock additional fields:

| Permission | Fields Unlocked |
|------------|-----------------|
| `READ_PHONE_STATE` | SIM operator, carrier, multi-SIM |
| `ACCESS_COARSE_LOCATION` | Coarse lat/lng, accuracy |
| `ACCESS_FINE_LOCATION` | WiFi SSID/BSSID hash, frequency |

Missing fields are reported as `null` in the data and listed in `CollectResult.missingFields`.

---

## ProGuard / R8

Consumer ProGuard rules ship with the AAR — no manual configuration needed.

---

## Documentation

- [SDK API Documentation](docs/SDK_DOCUMENTATION.md) — full API reference with all module fields
- [Publishing Guide](docs/PUBLISHING.md) — step-by-step Maven Central deployment

---

## Architecture

```
DeviceSdk (public API)
  ├── SdkConfig (configuration)
  ├── ModuleOrchestrator (parallel execution, timeouts)
  │     ├── SignalModule interface
  │     │     └── AppModule, HardwareModule, ScreenModule, ...
  │     └── ModuleCache (static module caching)
  ├── IdentityStore (encrypted persistent IDs)
  ├── SealedBoxCrypto (NaCl sealed box encryption)
  ├── DeviceSessionBuilder (structured sessions)
  └── JsonPayloadBuilder (JSON serialization)
```

---

## License

```
Copyright 2026 Behruz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
