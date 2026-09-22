package tj.behruz.devicesignals.sdk.internal.modules

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule

internal class AppModule : SignalModule {
    override val module = Module.APP

    override suspend fun collect(context: CollectContext): ModuleResult {
        val pm = context.appContext.packageManager
        val packageName = context.appContext.packageName
        val pi = pm.getPackageInfo(packageName, 0)
        val ai = pm.getApplicationInfo(packageName, 0)

        val installSource = try {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } catch (_: Exception) {
            null
        }

        val data = mapOf<String, Any?>(
            "app_name" to pm.getApplicationLabel(ai).toString(),
            "package_name" to packageName,
            "version_name" to (pi.versionName ?: ""),
            "version_code" to pi.longVersionCode,
            "install_source" to installSource,
            "first_install_time" to pi.firstInstallTime,
            "last_update_time" to pi.lastUpdateTime,
            "is_system_app" to ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0),
            "app_guid" to context.identityStore.getAppGuid(),
        )
        return ModuleResult(data = data, missingFields = emptyList())
    }
}
