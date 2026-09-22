package tj.behruz.devicesignals.sdk.internal.modules

import android.content.pm.PackageManager
import android.os.Build
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule
import tj.behruz.devicesignals.sdk.internal.SignatureStore
import java.io.File

internal class IntegrityModule : SignalModule {
    override val module = Module.INTEGRITY

    override suspend fun collect(context: CollectContext): ModuleResult {
        val rootIndicators = mutableListOf<String>()

        // Check su binary paths
        for (path in SignatureStore.suPaths) {
            if (File(path).exists()) {
                rootIndicators.add(path)
            }
        }

        // Check root management apps
        val pm = context.appContext.packageManager
        for (pkg in SignatureStore.rootAppPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                rootIndicators.add(pkg)
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        // Check Build.TAGS for test-keys
        if (Build.TAGS?.contains("test-keys") == true) {
            rootIndicators.add("test-keys")
        }

        // Check writable /system
        try {
            val systemDir = File("/system")
            if (systemDir.canWrite()) {
                rootIndicators.add("/system:writable")
            }
        } catch (_: Exception) { }

        // Check Magisk
        try {
            val magiskDir = File("/sbin/.magisk")
            if (magiskDir.exists()) {
                rootIndicators.add("magisk")
            }
        } catch (_: Exception) { }

        val isRooted = rootIndicators.isNotEmpty()

        // Bootloader state
        val bootloaderState = getBootloaderState()

        // SELinux status
        val seLinuxStatus = getSeLinuxStatus()

        // Verified boot
        val isVerifiedBoot = getSystemProperty("ro.boot.verifiedbootstate") == "green"

        val data = mapOf<String, Any?>(
            "is_rooted" to isRooted,
            "root_indicators" to rootIndicators.toList(),
            "bootloader_state" to bootloaderState,
            "se_linux_status" to seLinuxStatus,
            "is_verified_boot" to isVerifiedBoot,
        )
        return ModuleResult(data = data, missingFields = emptyList())
    }

    private fun getBootloaderState(): String {
        val state = getSystemProperty("ro.boot.flash.locked")
        return when (state) {
            "1" -> "LOCKED"
            "0" -> "UNLOCKED"
            else -> {
                val vbState = getSystemProperty("ro.boot.verifiedbootstate")
                when (vbState) {
                    "green" -> "LOCKED"
                    "orange", "yellow" -> "UNLOCKED"
                    else -> "UNKNOWN"
                }
            }
        }
    }

    private fun getSeLinuxStatus(): String {
        return try {
            val process = Runtime.getRuntime().exec("getenforce")
            try {
                val output = process.inputStream.bufferedReader().use { it.readLine()?.trim() ?: "" }
                process.waitFor()
                when (output.lowercase()) {
                    "enforcing" -> "ENFORCING"
                    "permissive" -> "PERMISSIVE"
                    "disabled" -> "DISABLED"
                    else -> "ENFORCING"
                }
            } finally {
                process.destroy()
            }
        } catch (_: Exception) {
            try {
                val enforce = File("/sys/fs/selinux/enforce").readText().trim()
                if (enforce == "1") "ENFORCING" else "PERMISSIVE"
            } catch (_: Exception) {
                "ENFORCING"
            }
        }
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            try {
                val value = process.inputStream.bufferedReader().use { it.readLine()?.trim() }
                process.waitFor()
                value?.ifEmpty { null }
            } finally {
                process.destroy()
            }
        } catch (_: Exception) {
            null
        }
    }
}
