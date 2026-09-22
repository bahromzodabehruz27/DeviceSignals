/**
 * Device Signals SDK — Public API Contract
 *
 * This file defines the public API surface of the SDK.
 * All types here are part of the stable contract exposed to host applications.
 * Internal implementation details are excluded.
 *
 * Package: tj.behruz.devicesignals.sdk
 */

// ─── Entry Point ────────────────────────────────────────────────────────────

object DeviceSdk {

    /**
     * Initialize the SDK. Must be called once before [collect].
     * Completes in < 20 ms with no disk I/O on the calling thread.
     *
     * @param context Application context (retained as applicationContext)
     * @param config Optional configuration; defaults applied if omitted
     */
    @JvmStatic
    fun init(context: Context, config: SdkConfig = SdkConfig())

    /**
     * Collect device signals. Suspend function for Kotlin callers.
     *
     * @param sessionId Caller-provided session identifier (1–128 chars, non-blank)
     * @param visitorId Caller-provided visitor identifier (1–128 chars, non-blank)
     * @param modules Modules to collect; defaults to all enabled modules
     * @return CollectResult with status, data, and missing fields
     * @throws DeviceSdkException.NotInitializedException if [init] was not called
     * @throws DeviceSdkException.InvalidArgumentException if sessionId/visitorId invalid
     */
    suspend fun collect(
        sessionId: String,
        visitorId: String,
        modules: Set<Module> = Module.ALL,
    ): CollectResult

    /**
     * Collect device signals. Callback variant for Java callers.
     * Callback is invoked on a background thread.
     */
    @JvmStatic
    fun collect(
        sessionId: String,
        visitorId: String,
        callback: CollectCallback,
    )

    /**
     * Collect device signals with module selection. Callback variant for Java callers.
     */
    @JvmStatic
    fun collect(
        sessionId: String,
        visitorId: String,
        modules: Set<Module>,
        callback: CollectCallback,
    )

    /**
     * Attach an Activity for THREATS module overlay/screen-capture detection.
     * Auto-detaches when the Activity is destroyed (lifecycle observer).
     */
    @JvmStatic
    fun attach(activity: Activity)

    /**
     * Manually detach the current Activity. Optional — auto-detach handles cleanup.
     */
    @JvmStatic
    fun detach()
}

// ─── Configuration ──────────────────────────────────────────────────────────

data class SdkConfig(
    val timeoutMs: Long = 3_000L,
    val moduleTimeoutMs: Long = 1_500L,
    val enabledModules: Set<Module> = Module.ALL,
    val playIntegrityCloudProjectNumber: String? = null,
    val logLevel: LogLevel = LogLevel.NONE,
    val logger: SdkLogger? = null,
    val onModuleCompleted: ((Module, Long, ModuleStatus) -> Unit)? = null,
)

// ─── Result ─────────────────────────────────────────────────────────────────

data class CollectResult(
    val status: CollectStatus,
    val moduleData: Map<Module, Map<String, Any?>>,
    val missingFields: List<String>,
    val durationMs: Long,
) {
    /** Serialize to JSON string conforming to schema version 1.0 */
    fun toJson(): String
}

// ─── Callback (Java interop) ────────────────────────────────────────────────

fun interface CollectCallback {
    fun onResult(result: CollectResult)
}

// ─── Enums ──────────────────────────────────────────────────────────────────

enum class Module(val jsonKey: String, val isStatic: Boolean) {
    APP("app", true),
    HARDWARE("hardware", true),
    SCREEN("screen", true),
    INTEGRITY("integrity", false),
    PLAY_INTEGRITY("play_integrity", false),
    DEBUG("debug", false),
    THREATS("threats", false),
    TELEPHONY("telephony", false),
    NETWORK("network", false),
    LOCATION("location", false),
    POWER("power", false),
    SYSTEM("system", false);

    companion object {
        @JvmField
        val ALL: Set<Module> = entries.toSet()
    }
}

enum class CollectStatus { SUCCESS, PARTIAL, FAILED }

enum class LogLevel { NONE, ERROR, WARN, DEBUG }

enum class ModuleStatus { SUCCESS, PARTIAL, FAILED, SKIPPED }

// ─── Logging & Observability ────────────────────────────────────────────────

interface SdkLogger {
    fun log(level: LogLevel, tag: String, message: String)
}

// ─── Exceptions ─────────────────────────────────────────────────────────────

open class DeviceSdkException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    class NotInitializedException(
        message: String = "DeviceSdk.init() must be called before collect()",
    ) : DeviceSdkException(message)

    class InvalidArgumentException(
        message: String,
    ) : DeviceSdkException(message)
}
