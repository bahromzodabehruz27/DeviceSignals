package tj.behruz.devicesignals.sdk.internal

import android.app.Activity
import android.content.Context
import tj.behruz.devicesignals.sdk.Module

internal data class CollectContext(
    val appContext: Context,
    val identityStore: IdentityStore,
    val activityRef: java.lang.ref.WeakReference<Activity>?,
)

internal interface SignalModule {
    val module: Module
    suspend fun collect(context: CollectContext): ModuleResult
}
