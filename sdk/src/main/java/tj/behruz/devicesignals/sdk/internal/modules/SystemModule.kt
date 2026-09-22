package tj.behruz.devicesignals.sdk.internal.modules

import android.os.Build
import android.os.SystemClock
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule
import java.util.Locale

internal class SystemModule : SignalModule {
    override val module = Module.SYSTEM

    override suspend fun collect(context: CollectContext): ModuleResult {
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
            "elapsed_realtime_ms" to SystemClock.elapsedRealtime(),
        )
        return ModuleResult(data = data, missingFields = emptyList())
    }
}
