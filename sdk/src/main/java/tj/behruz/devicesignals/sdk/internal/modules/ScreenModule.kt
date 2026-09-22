package tj.behruz.devicesignals.sdk.internal.modules

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule

internal class ScreenModule : SignalModule {
    override val module = Module.SCREEN

    override suspend fun collect(context: CollectContext): ModuleResult {
        val wm = context.appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = context.appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.appContext.resources.displayMetrics
        val refreshRate: Float = defaultDisplay?.refreshRate ?: 60f

        val densityBucket = when {
            metrics.densityDpi <= DisplayMetrics.DENSITY_LOW -> "ldpi"
            metrics.densityDpi <= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
            metrics.densityDpi <= DisplayMetrics.DENSITY_HIGH -> "hdpi"
            metrics.densityDpi <= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
            metrics.densityDpi <= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
            else -> "xxxhdpi"
        }

        val orientation = if (context.appContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "landscape"
        } else {
            "portrait"
        }

        val data = mapOf<String, Any?>(
            "width_px" to metrics.widthPixels,
            "height_px" to metrics.heightPixels,
            "density_dpi" to metrics.densityDpi,
            "density_bucket" to densityBucket,
            "refresh_rate_hz" to refreshRate,
            "orientation" to orientation,
            "font_scale" to context.appContext.resources.configuration.fontScale,
        )
        return ModuleResult(data = data, missingFields = emptyList())
    }
}
