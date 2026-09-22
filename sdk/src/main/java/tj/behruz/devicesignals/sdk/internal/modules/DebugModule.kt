package tj.behruz.devicesignals.sdk.internal.modules

import android.content.pm.ApplicationInfo
import android.os.Debug
import android.provider.Settings
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule
import tj.behruz.devicesignals.sdk.internal.SignatureStore
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

internal class DebugModule : SignalModule {
    override val module = Module.DEBUG

    override suspend fun collect(context: CollectContext): ModuleResult {
        val ai = context.appContext.applicationInfo
        val isDebuggable = (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isDebuggerAttached = Debug.isDebuggerConnected()

        val isUsbDebugging = try {
            Settings.Global.getInt(context.appContext.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }

        val tamperingIndicators = mutableListOf<String>()

        // Scan /proc/self/maps for Frida libraries
        try {
            val maps = File("/proc/self/maps").readText()
            for (lib in SignatureStore.fridaLibraries) {
                if (maps.contains(lib, ignoreCase = true)) {
                    tamperingIndicators.add(lib)
                }
            }
        } catch (_: Exception) { }

        // Check for Xposed
        for (artifact in SignatureStore.xposedArtifacts) {
            try {
                Class.forName(artifact)
                tamperingIndicators.add(artifact)
            } catch (_: ClassNotFoundException) { }
        }

        // Check TracerPid
        try {
            val status = File("/proc/self/status").readText()
            val tracerPid = Regex("TracerPid:\\s+(\\d+)").find(status)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (tracerPid > 0) {
                tamperingIndicators.add("tracer_pid:$tracerPid")
            }
        } catch (_: Exception) { }

        // Check Frida default port
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", 27042), 100)
            socket.close()
            tamperingIndicators.add("frida_port:27042")
        } catch (_: Exception) { }

        val data = mapOf<String, Any?>(
            "is_debuggable" to isDebuggable,
            "is_debugger_attached" to isDebuggerAttached,
            "is_usb_debugging_enabled" to isUsbDebugging,
            "is_runtime_tampering_detected" to tamperingIndicators.isNotEmpty(),
            "tampering_indicators" to tamperingIndicators.toList(),
        )
        return ModuleResult(data = data, missingFields = emptyList())
    }
}
