package tj.behruz.devicesignals.sdk

import android.app.Activity
import android.content.Context
import android.util.Base64
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.behruz.devicesignals.sdk.internal.DeviceSessionBuilder
import tj.behruz.devicesignals.sdk.internal.IdentityStore
import tj.behruz.devicesignals.sdk.internal.ModuleOrchestrator
import tj.behruz.devicesignals.sdk.internal.SealedBoxCrypto
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.SignalModule
import tj.behruz.devicesignals.sdk.internal.cache.ModuleCache
import tj.behruz.devicesignals.sdk.internal.modules.AppModule
import tj.behruz.devicesignals.sdk.internal.modules.DebugModule
import tj.behruz.devicesignals.sdk.internal.modules.HardwareModule
import tj.behruz.devicesignals.sdk.internal.modules.IntegrityModule
import tj.behruz.devicesignals.sdk.internal.modules.LocationModule
import tj.behruz.devicesignals.sdk.internal.modules.NetworkModule
import tj.behruz.devicesignals.sdk.internal.modules.PlayIntegrityModule
import tj.behruz.devicesignals.sdk.internal.modules.PowerModule
import tj.behruz.devicesignals.sdk.internal.modules.ScreenModule
import tj.behruz.devicesignals.sdk.internal.modules.SystemModule
import tj.behruz.devicesignals.sdk.internal.modules.TelephonyModule
import tj.behruz.devicesignals.sdk.internal.modules.ThreatsModule
import java.lang.ref.WeakReference

object DeviceSdk {

    @Volatile
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var config: SdkConfig
    private lateinit var orchestrator: ModuleOrchestrator
    private lateinit var identityStore: IdentityStore

    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private val moduleRegistry = mutableListOf<SignalModule>()

    @JvmStatic
    fun init(context: Context, config: SdkConfig = SdkConfig()) {
        this.appContext = context.applicationContext
        this.config = config
        this.identityStore = IdentityStore(appContext)
        ModuleCache.reset()

        val modules = mutableListOf(
            AppModule(),
            HardwareModule(),
            ScreenModule(),
            IntegrityModule(),
            DebugModule(),
            ThreatsModule(),
            TelephonyModule(),
            NetworkModule(),
            LocationModule(),
            PowerModule(),
            SystemModule(),
        )
        val projectNumber = config.playIntegrityCloudProjectNumber
        if (projectNumber != null) {
            modules.add(PlayIntegrityModule(projectNumber))
        }
        registerModules(modules)

        this.initialized = true
    }

    internal fun registerModules(modules: List<SignalModule>) {
        moduleRegistry.clear()
        moduleRegistry.addAll(modules)
        orchestrator = ModuleOrchestrator(moduleRegistry, config)
    }

    @JvmStatic
    suspend fun collect(
        sessionId: String,
        visitorId: String,
        modules: Set<Module> = Module.ALL,
    ): CollectResult {
        ensureInitialized()
        validateArgs(sessionId, visitorId)
        return withContext(Dispatchers.Default) {
            orchestrator.collect(collectContext(), sessionId, visitorId, modules)
        }
    }

    @JvmStatic
    fun collect(
        sessionId: String,
        visitorId: String,
        callback: CollectCallback,
    ) {
        collect(sessionId, visitorId, Module.ALL, callback)
    }

    @JvmStatic
    fun collect(
        sessionId: String,
        visitorId: String,
        modules: Set<Module>,
        callback: CollectCallback,
    ) {
        ensureInitialized()
        validateArgs(sessionId, visitorId)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val result = orchestrator.collect(collectContext(), sessionId, visitorId, modules)
                callback.onResult(result)
            } catch (_: Exception) {
                callback.onResult(
                    CollectResult(
                        status = CollectStatus.FAILED,
                        moduleData = emptyMap(),
                        missingFields = emptyList(),
                        durationMs = 0,
                        sessionId = sessionId,
                        visitorId = visitorId,
                    )
                )
            }
        }
    }

    @JvmStatic
    fun getDeviceId(): String {
        ensureInitialized()
        return config.deviceId
            ?: throw DeviceSdkException.InvalidArgumentException(
                "deviceId must be set in SdkConfig"
            )
    }

    @JvmStatic
    suspend fun buildDeviceSession(
        sessionId: String,
        visitorId: String,
    ): String {
        ensureInitialized()
        val deviceId = getDeviceId()
        val fraudKeyBase64 = config.fraudPublicKeyBase64
            ?: throw DeviceSdkException.InvalidArgumentException(
                "fraudPublicKeyBase64 must be set in SdkConfig"
            )
        val fraudPublicKey = SealedBoxCrypto.decodeAndValidatePublicKey(fraudKeyBase64)

        val collectResult = withContext(Dispatchers.Default) {
            orchestrator.collect(collectContext(), sessionId, visitorId, Module.ALL)
        }

        val appGuid = identityStore.getAppGuid()
        val firstSeen = identityStore.getFirstSeen()

        val session = DeviceSessionBuilder.build(
            collectResult = collectResult,
            deviceId = deviceId,
            appGuid = appGuid,
            firstSeen = firstSeen,
        )

        val json = session.toJson()
        val sealed = SealedBoxCrypto.seal(json.toByteArray(Charsets.UTF_8), fraudPublicKey)
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    @JvmStatic
    fun buildDeviceSession(
        sessionId: String,
        visitorId: String,
        callback: (String?) -> Unit,
    ) {
        ensureInitialized()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val result = buildDeviceSession(sessionId, visitorId)
                callback(result)
            } catch (_: Exception) {
                callback(null)
            }
        }
    }

    @JvmStatic
    fun attach(activity: Activity) {
        ensureInitialized()
        // Remove previous observer if any
        detachInternal()

        activityRef = WeakReference(activity)

        if (activity is LifecycleOwner) {
            val observer = object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    detachInternal()
                }
            }
            lifecycleObserver = observer
            activity.lifecycle.addObserver(observer)
        }
    }

    @JvmStatic
    fun detach() {
        detachInternal()
    }

    private fun detachInternal() {
        val activity = activityRef?.get()
        if (activity is LifecycleOwner && lifecycleObserver != null) {
            activity.lifecycle.removeObserver(lifecycleObserver!!)
        }
        lifecycleObserver = null
        activityRef = null
    }

    private fun collectContext() = CollectContext(
        appContext = appContext,
        identityStore = identityStore,
        activityRef = activityRef,
    )

    private fun ensureInitialized() {
        if (!initialized) {
            throw DeviceSdkException.NotInitializedException()
        }
    }

    private fun validateArgs(sessionId: String, visitorId: String) {
        if (sessionId.isBlank()) {
            throw DeviceSdkException.InvalidArgumentException("sessionId must not be empty or blank")
        }
        if (sessionId.length > 128) {
            throw DeviceSdkException.InvalidArgumentException("sessionId must not exceed 128 characters")
        }
        if (visitorId.isBlank()) {
            throw DeviceSdkException.InvalidArgumentException("visitorId must not be empty or blank")
        }
        if (visitorId.length > 128) {
            throw DeviceSdkException.InvalidArgumentException("visitorId must not exceed 128 characters")
        }
    }
}
