package tj.behruz.devicesignals.sdk.internal.modules

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule
import java.io.File

internal class HardwareModule : SignalModule {
    override val module = Module.HARDWARE

    override suspend fun collect(context: CollectContext): ModuleResult {
        val am = context.appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorageMb = stat.totalBytes / (1024 * 1024)

        val socManufacturer: String? = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else null
        val socModel: String? = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null

        val emulatorSignals = listOf(
            Build.FINGERPRINT.contains("generic", ignoreCase = true),
            Build.FINGERPRINT.contains("sdk", ignoreCase = true),
            Build.MODEL.contains("Emulator", ignoreCase = true),
            Build.MODEL.contains("Android SDK", ignoreCase = true),
            Build.HARDWARE.equals("goldfish", ignoreCase = true),
            Build.HARDWARE.equals("ranchu", ignoreCase = true),
            Build.PRODUCT.startsWith("sdk", ignoreCase = true),
            Build.BRAND.equals("generic", ignoreCase = true),
            File("/dev/qemu_pipe").exists(),
            File("/dev/goldfish_pipe").exists(),
        )

        val data = mapOf<String, Any?>(
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "board" to Build.BOARD,
            "hardware" to Build.HARDWARE,
            "soc_manufacturer" to socManufacturer,
            "soc_model" to socModel,
            "total_ram_mb" to (memInfo.totalMem / (1024 * 1024)),
            "total_storage_mb" to totalStorageMb,
            "cpu_cores" to Runtime.getRuntime().availableProcessors(),
            "cpu_architecture" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
            "is_emulator" to (emulatorSignals.count { it } >= 2),
        )
        return ModuleResult(data = data, missingFields = emptyList())
    }
}
