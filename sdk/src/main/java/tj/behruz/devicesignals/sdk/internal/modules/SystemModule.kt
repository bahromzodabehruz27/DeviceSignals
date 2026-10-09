package tj.behruz.devicesignals.sdk.internal.modules

import android.app.KeyguardManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.DeviceIdentifiers
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule
import java.util.Locale

internal class SystemModule : SignalModule {
    override val module = Module.SYSTEM

    override suspend fun collect(context: CollectContext): ModuleResult {
        val appContext = context.appContext
        val drmId = DeviceIdentifiers.drmId()
        val androidId = DeviceIdentifiers.androidId(appContext)

        val developerOptionsEnabled: Boolean? = try {
            Settings.Global.getInt(appContext.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (_: Exception) {
            null
        }

        val isKeyguardSecure: Boolean? = try {
            (appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        } catch (_: Exception) {
            null
        }

        val regionCountry = Resources.getSystem().configuration.locales[0].country.ifEmpty { null }

        val elapsedRealtimeMs = SystemClock.elapsedRealtime()
        val data = mapOf<String, Any?>(
            "os_version" to Build.VERSION.RELEASE,
            "api_level" to Build.VERSION.SDK_INT,
            "build_fingerprint" to Build.FINGERPRINT,
            "build_tags" to (Build.TAGS ?: ""),
            "build_type" to Build.TYPE,
            "security_patch_level" to Build.VERSION.SECURITY_PATCH,
            "device_id" to context.identityStore.getDeviceId(),
            "language" to Locale.getDefault().toLanguageTag(),
            "uptime_ms" to SystemClock.uptimeMillis(),
            "elapsed_realtime_ms" to elapsedRealtimeMs,
            "boot_time_ms" to (System.currentTimeMillis() - elapsedRealtimeMs),
            "drm_id" to drmId,
            "android_id" to androidId,
            "developer_options_enabled" to developerOptionsEnabled,
            "is_keyguard_secure" to isKeyguardSecure,
            "region_country" to regionCountry,
        )
        val missingFields = listOf(
            "drm_id" to drmId,
            "android_id" to androidId,
            "developer_options_enabled" to developerOptionsEnabled,
            "is_keyguard_secure" to isKeyguardSecure,
            "region_country" to regionCountry,
        ).filter { it.second == null }.map { it.first }
        return ModuleResult(data = data, missingFields = missingFields)
    }
}
