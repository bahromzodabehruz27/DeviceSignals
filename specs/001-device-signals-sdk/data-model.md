# Data Model: Device Signals SDK

**Date**: 2026-09-19 | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

## Entities

### DeviceSdk (Singleton)

The entry point for all SDK operations.

| Property | Type | Description |
|----------|------|-------------|
| `isInitialized` | Boolean | Whether `init()` has been called |
| `config` | SdkConfig | Active configuration (set during init) |
| `activityRef` | WeakReference\<Activity\>? | Attached Activity for THREATS module |
| `identityStore` | IdentityStore (lazy) | Manages device_id and app_guid |

**Operations**: `init(context, config?)`, `collect(sessionId, visitorId, modules?)`, `attach(activity)`, `detach()`

**State transitions**:
```
UNINITIALIZED → [init()] → READY → [attach()] → READY_WITH_ACTIVITY
                                  → [detach() / Activity destroyed] → READY
```

### SdkConfig

Immutable configuration provided at init time.

| Field | Type | Default | Validation |
|-------|------|---------|------------|
| `timeoutMs` | Long | 3000 | > 0 |
| `moduleTimeoutMs` | Long | 1500 | > 0, ≤ timeoutMs |
| `enabledModules` | Set\<Module\> | Module.ALL | Non-empty |
| `playIntegrityCloudProjectNumber` | String? | null | If non-null, non-blank |
| `logLevel` | LogLevel | NONE | — |
| `logger` | SdkLogger? | null | — |
| `onModuleCompleted` | ((Module, Long, ModuleStatus) → Unit)? | null | — |

### Module (Enum)

| Value | JSON Key | Static? | Notes |
|-------|----------|---------|-------|
| APP | `app` | Yes | Cached for process lifetime |
| HARDWARE | `hardware` | Yes | Cached for process lifetime |
| SCREEN | `screen` | Yes | Cached for process lifetime |
| INTEGRITY | `integrity` | No | |
| PLAY_INTEGRITY | `play_integrity` | No | Auto-excluded if no cloud project number |
| DEBUG | `debug` | No | |
| THREATS | `threats` | No | Requires attached Activity for overlay/capture |
| TELEPHONY | `telephony` | No | Requires READ_PHONE_STATE for most fields |
| NETWORK | `network` | No | |
| LOCATION | `location` | No | Requires ACCESS_COARSE_LOCATION for coordinates |
| POWER | `power` | No | |
| SYSTEM | `system` | No | |

**Companion**: `Module.ALL: Set<Module>` — all 12 modules.

### CollectResult

The outcome of a `collect()` call.

| Field | Type | Description |
|-------|------|-------------|
| `status` | CollectStatus | SUCCESS, PARTIAL, or FAILED |
| `moduleData` | Map\<Module, Map\<String, Any?\>\> | Per-module signal data |
| `missingFields` | List\<String\> | Fields not collected, format: `"<module>.<field>"` |
| `durationMs` | Long | Total collection time in milliseconds |
| `metadata` | PayloadMetadata | Session and collection metadata |

**Operations**: `toJson(): String` — serializes the full payload.

**Status determination**:
- `SUCCESS`: `missingFields` is empty
- `PARTIAL`: at least one module returned data, but `missingFields` is non-empty
- `FAILED`: no modules completed (global timeout or all modules failed)

### PayloadMetadata

Top-level metadata included in every JSON payload.

| Field | Type | Source |
|-------|------|--------|
| `schemaVersion` | String | Always `"1.0"` |
| `id` | String | UUID v4 generated per collect() call |
| `sessionId` | String | From collect() parameter |
| `visitorId` | String | From collect() parameter |
| `collectedAt` | String | ISO 8601 UTC timestamp |
| `platform` | String | Always `"android"` |

### ModuleResult (Internal)

Per-module outcome returned by each `SignalModule.collect()`.

| Field | Type | Description |
|-------|------|-------------|
| `data` | Map\<String, Any?\> | Collected field values (null for failed fields) |
| `missingFields` | List\<String\> | Field names that could not be collected |

### SignalModule (Internal Interface)

| Property/Method | Type | Description |
|-----------------|------|-------------|
| `module` | Module | Which module this implements |
| `collect(context)` | suspend → ModuleResult | Collect signals for this module |

### IdentityStore (Internal)

Manages persistent identifiers via EncryptedSharedPreferences.

| Field | Type | Persistence |
|-------|------|-------------|
| `deviceId` | String (UUID v7) | EncryptedSharedPreferences; regenerates on data clear |
| `appGuid` | String (UUID v4) | EncryptedSharedPreferences; regenerates on data clear |

### DeviceSdkException (Exception Hierarchy)

```
DeviceSdkException (open class, extends RuntimeException)
├── NotInitializedException   — collect() called before init()
└── InvalidArgumentException  — invalid sessionId/visitorId
```

### LogLevel (Enum)

| Value | Description |
|-------|-------------|
| NONE | No logging |
| ERROR | Errors only |
| WARN | Warnings and errors |
| DEBUG | All log output (development only, no signal values per NFR-011) |

### ModuleStatus (Enum)

Used in the `onModuleCompleted` callback.

| Value | Description |
|-------|-------------|
| SUCCESS | All fields collected |
| PARTIAL | Some fields missing (permissions, API level) |
| FAILED | Module threw or timed out |
| SKIPPED | Module not in effective set |

## Relationships

```
DeviceSdk 1──1 SdkConfig
DeviceSdk 1──1 IdentityStore
DeviceSdk 1──* SignalModule (12 implementations)
SignalModule *──1 Module (enum identity)
SignalModule → ModuleResult (collect output)
CollectResult 1──1 PayloadMetadata
CollectResult 1──* ModuleResult (aggregated)
DeviceSdkException <|── NotInitializedException
DeviceSdkException <|── InvalidArgumentException
```

## JSON Payload Structure

```json
{
  "schema_version": "1.0",
  "id": "uuid-per-call",
  "session_id": "caller-provided",
  "visitor_id": "caller-provided",
  "collected_at": "2026-09-19T12:00:00.000Z",
  "platform": "android",
  "collection": {
    "status": "SUCCESS|PARTIAL|FAILED",
    "duration_ms": 342,
    "modules_collected": ["app", "hardware", "screen"],
    "missing_fields": ["telephony.sim_operator", "telephony.sim_country_iso"]
  },
  "app": { ... },
  "hardware": { ... },
  "screen": { ... }
}
```

Module objects are present only for collected modules (FR-008). Field schemas are defined in the spec's Module Field Definitions section.
