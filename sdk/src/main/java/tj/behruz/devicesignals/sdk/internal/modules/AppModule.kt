package tj.behruz.devicesignals.sdk.internal.modules

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import androidx.core.content.pm.PackageInfoCompat
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
            if (Build.VERSION.SDK_INT >= 30) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }

        val data = mapOf<String, Any?>(
            "app_name" to pm.getApplicationLabel(ai).toString(),
            "package_name" to packageName,
            "version_name" to (pi.versionName ?: ""),
            "version_code" to PackageInfoCompat.getLongVersionCode(pi),
            "install_source" to installSource,
            "first_install_time" to pi.firstInstallTime,
            "last_update_time" to pi.lastUpdateTime,
            "is_system_app" to ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0),
            "app_guid" to context.identityStore.getAppGuid(),
            "is_app_cloned" to isAppCloned(packageName, ai.dataDir),
        )
        return ModuleResult(data = data, missingFields = emptyList())
    }

    /**
     * Dual-app features (MIUI "Dual apps" = user 999, Samsung "Dual Messenger" = 95)
     * run the clone in a dedicated user; virtual-space cloners (Parallel Space etc.)
     * run it under their own data directory.
     */
    private fun isAppCloned(packageName: String, dataDir: String?): Boolean {
        val userId = Process.myUid() / PER_USER_RANGE
        if (userId in CLONE_USER_IDS) return true
        val dir = dataDir ?: return false
        val expected = listOf(
            "/data/data/$packageName",
            "/data/user/$userId/$packageName",
            "/data/user_de/$userId/$packageName",
        )
        // Adopted storage: /mnt/expand/<uuid>/user/<id>/<pkg>
        if (dir.startsWith("/mnt/expand/") && dir.endsWith("/user/$userId/$packageName")) return false
        return expected.none { dir == it }
    }

    private companion object {
        const val PER_USER_RANGE = 100_000
        val CLONE_USER_IDS = setOf(95, 999)
    }
}
