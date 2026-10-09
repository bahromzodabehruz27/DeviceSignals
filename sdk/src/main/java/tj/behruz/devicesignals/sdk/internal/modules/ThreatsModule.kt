package tj.behruz.devicesignals.sdk.internal.modules

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule
import tj.behruz.devicesignals.sdk.internal.SignatureStore
import java.util.concurrent.Executor
import java.util.function.Consumer

internal class ThreatsModule : SignalModule {
    override val module = Module.THREATS

    override suspend fun collect(context: CollectContext): ModuleResult {
        val missingFields = mutableListOf<String>()
        val appContext = context.appContext
        val pm = appContext.packageManager

        // Installed threat apps
        val threatApps = mutableListOf<String>()
        for (pkg in SignatureStore.ratPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                threatApps.add(pkg)
            } catch (_: PackageManager.NameNotFoundException) { }
        }

        // Overlay detection - requires Activity
        val activity: Activity? = context.activityRef?.get()
        val isOverlayDetected: Boolean?

        if (activity != null) {
            isOverlayDetected = detectOverlay(activity)
        } else {
            isOverlayDetected = null
            missingFields.add("is_overlay_detected")
        }

        val isScreenBeingCaptured = isAppBeingRecorded(activity) || hasMirroringDisplay(appContext)

        // Accessibility services
        val am = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val isAccessibilityActive = am.isEnabled
        val accessibilityServices = try {
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .mapNotNull { it.resolveInfo?.serviceInfo?.let { si -> "${si.packageName}/${si.name}" } }
        } catch (_: Exception) {
            emptyList()
        }

        // Remote control detection
        val isRemoteControlDetected = threatApps.isNotEmpty()

        val data = mapOf<String, Any?>(
            "installed_threat_apps" to threatApps.toList(),
            "is_overlay_detected" to isOverlayDetected,
            "is_screen_being_captured" to isScreenBeingCaptured,
            "is_accessibility_service_active" to isAccessibilityActive,
            "accessibility_services" to accessibilityServices,
            "is_remote_control_detected" to isRemoteControlDetected,
        )
        return ModuleResult(data = data, missingFields = missingFields)
    }

    /** API 35+: asks the system whether this app's windows are visible to a screen recorder. */
    private fun isAppBeingRecorded(activity: Activity?): Boolean {
        if (activity == null || Build.VERSION.SDK_INT < 35) return false
        return try {
            val wm = activity.windowManager
            val callback = Consumer<Int> { }
            val state = wm.addScreenRecordingCallback(Executor { it.run() }, callback)
            wm.removeScreenRecordingCallback(callback)
            state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
        } catch (_: Exception) {
            false
        }
    }

    /** Casting / mirroring shows up as an extra non-private display. */
    private fun hasMirroringDisplay(context: Context): Boolean = try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.displays.any { it.displayId != Display.DEFAULT_DISPLAY && (it.flags and Display.FLAG_PRIVATE) == 0 }
    } catch (_: Exception) {
        false
    }

    private fun detectOverlay(activity: Activity): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                // Use WindowInsets to check if any app overlay is currently visible
                val rootView = activity.window.decorView
                rootView.rootWindowInsets?.let { insets ->
                    // Check if touches are being obscured by another window
                    val visibleFrame = android.graphics.Rect()
                    rootView.getWindowVisibleDisplayFrame(visibleFrame)
                    val screenHeight = activity.resources.displayMetrics.heightPixels
                    val screenWidth = activity.resources.displayMetrics.widthPixels
                    // If visible frame is significantly smaller, an overlay may be present
                    val visibleArea = visibleFrame.width().toLong() * visibleFrame.height()
                    val screenArea = screenWidth.toLong() * screenHeight
                    visibleArea < screenArea * 0.85
                } ?: false
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
