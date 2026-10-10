package tj.behruz.devicesignals.sdk.internal.modules

import android.content.Context
import android.media.AudioManager
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CallTracker
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule

internal class BehaviorModule : SignalModule {
    override val module = Module.BEHAVIOR

    override suspend fun collect(context: CollectContext): ModuleResult {
        val am = context.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Audio mode covers both carrier and VoIP calls and needs no permission.
        val isCallActive = CallTracker.isCallMode(am.mode)
        if (isCallActive) CallTracker.markCall()

        val isMicrophoneActive: Boolean? = try {
            am.activeRecordingConfigurations.isNotEmpty()
        } catch (_: Exception) {
            null
        }

        val data = mapOf<String, Any?>(
            "is_call_active" to isCallActive,
            "call_detected_last_3_minutes" to CallTracker.seenWithinWindow(),
            "is_microphone_active" to isMicrophoneActive,
        )
        val missing = if (isMicrophoneActive == null) listOf("is_microphone_active") else emptyList()
        return ModuleResult(data = data, missingFields = missing)
    }
}
