# Device Signals SDK — Consumer ProGuard Rules
# These rules are automatically applied to host applications consuming this AAR.

# Keep all public API classes
-keep class tj.behruz.devicesignals.sdk.DeviceSdk { *; }
-keep class tj.behruz.devicesignals.sdk.SdkConfig { *; }
-keep class tj.behruz.devicesignals.sdk.CollectResult { *; }
-keep class tj.behruz.devicesignals.sdk.Module { *; }
-keep class tj.behruz.devicesignals.sdk.CollectStatus { *; }
-keep class tj.behruz.devicesignals.sdk.ModuleStatus { *; }
-keep class tj.behruz.devicesignals.sdk.LogLevel { *; }
-keep class tj.behruz.devicesignals.sdk.SdkLogger { *; }
-keep class tj.behruz.devicesignals.sdk.CollectCallback { *; }
-keep class tj.behruz.devicesignals.sdk.DeviceSdkException { *; }
-keep class tj.behruz.devicesignals.sdk.DeviceSdkException$NotInitializedException { *; }
-keep class tj.behruz.devicesignals.sdk.DeviceSdkException$InvalidArgumentException { *; }
-keep class tj.behruz.devicesignals.sdk.DeviceSdkException$EncryptionException { *; }
-keep class tj.behruz.devicesignals.sdk.DeviceSession { *; }
-keep class tj.behruz.devicesignals.sdk.DeviceSession$Device { *; }
-keep class tj.behruz.devicesignals.sdk.DeviceSession$Event { *; }
