package tj.behruz.devicesignals.sdk.internal.modules

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule

// Permission-gated calls are guarded by checkSelfPermission + SecurityException.
@SuppressLint("MissingPermission")
internal class TelephonyModule : SignalModule {
    override val module = Module.TELEPHONY

    override suspend fun collect(context: CollectContext): ModuleResult {
        val appContext = context.appContext
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val missingFields = mutableListOf<String>()

        val hasPermission = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val phoneType = when (tm.phoneType) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            else -> "NONE"
        }

        val simState = when (tm.simState) {
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
            else -> "UNKNOWN"
        }

        var simOperator: String? = null
        var simCountryIso: String? = null
        var networkOperator: String? = null
        var networkCountryIso: String? = null
        var isMultiSim: Boolean? = null
        var activeSimCount: Int? = null
        var esimCount: Int? = null

        if (hasPermission) {
            simOperator = tm.simOperator?.ifEmpty { null }
            simCountryIso = tm.simCountryIso?.ifEmpty { null }
            networkOperator = tm.networkOperator?.ifEmpty { null }
            networkCountryIso = tm.networkCountryIso?.ifEmpty { null }

            try {
                val sm = appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeList = sm.activeSubscriptionInfoList.orEmpty()
                activeSimCount = activeList.size
                isMultiSim = activeSimCount > 1
                if (Build.VERSION.SDK_INT >= 28) {
                    esimCount = activeList.count { it.isEmbedded }
                } else {
                    missingFields.add("esim_count")
                }
            } catch (_: SecurityException) {
                missingFields.addAll(listOf("is_multi_sim", "active_sim_count", "esim_count"))
            }
        } else {
            val permFields = listOf("sim_operator", "sim_country_iso", "network_operator",
                "network_country_iso", "is_multi_sim", "active_sim_count", "esim_count")
            missingFields.addAll(permFields)
        }

        val data = mapOf<String, Any?>(
            "sim_operator" to simOperator,
            "sim_country_iso" to simCountryIso,
            "network_operator" to networkOperator,
            "network_country_iso" to networkCountryIso,
            "phone_type" to phoneType,
            "sim_state" to simState,
            "is_multi_sim" to isMultiSim,
            "active_sim_count" to activeSimCount,
            "esim_count" to esimCount,
        )
        return ModuleResult(data = data, missingFields = missingFields)
    }
}
