package tj.behruz.devicesignals.sdk.internal.modules

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule
import tj.behruz.devicesignals.sdk.internal.SignatureStore

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
        val isScreenBeingCaptured: Boolean?

        if (activity != null) {
            isOverlayDetected = detectOverlay(activity)
            // Screen capture detection requires callback registration (API 34+),
            // which cannot be done at collection time — report as unavailable
            isScreenBeingCaptured = null
            missingFields.add("is_screen_being_captured")
        } else {
            isOverlayDetected = null
            isScreenBeingCaptured = null
            missingFields.add("is_overlay_detected")
            missingFields.add("is_screen_being_captured")
        }

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
