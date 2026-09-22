package tj.behruz.devicesignals.sdk.internal.modules

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule
import java.util.TimeZone

internal class LocationModule : SignalModule {
    override val module = Module.LOCATION

    override suspend fun collect(context: CollectContext): ModuleResult {
        val appContext = context.appContext
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val missingFields = mutableListOf<String>()

        val isLocationEnabled = if (Build.VERSION.SDK_INT >= 28) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

        val availableProviders = lm.getProviders(true)

        var latitudeCoarse: Double? = null
        var longitudeCoarse: Double? = null
        var accuracyM: Float? = null

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCoarseLocation) {
            try {
                @Suppress("DEPRECATION")
                val location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (location != null) {
                    latitudeCoarse = location.latitude
                    longitudeCoarse = location.longitude
                    accuracyM = location.accuracy
                }
            } catch (_: SecurityException) {
                missingFields.addAll(listOf("latitude_coarse", "longitude_coarse", "accuracy_m"))
            }
        } else {
            missingFields.addAll(listOf("latitude_coarse", "longitude_coarse", "accuracy_m"))
        }

        val tz = TimeZone.getDefault()

        val data = mapOf<String, Any?>(
            "is_location_enabled" to isLocationEnabled,
            "available_providers" to availableProviders,
            "latitude_coarse" to latitudeCoarse,
            "longitude_coarse" to longitudeCoarse,
            "accuracy_m" to accuracyM,
            "timezone_id" to tz.id,
            "timezone_offset_minutes" to (tz.rawOffset / 60000),
        )
        return ModuleResult(data = data, missingFields = missingFields)
    }
}
