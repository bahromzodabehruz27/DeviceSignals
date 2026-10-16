package tj.behruz.devicesignals.sdk.internal

import tj.behruz.devicesignals.sdk.LogLevel
import tj.behruz.devicesignals.sdk.SdkLogger

/**
 * Central SDK logger. Configured once in `DeviceSdk.init` from `SdkConfig.logLevel` / `SdkConfig.logger`.
 * Messages are only built when the level is enabled, so verbose DEBUG dumps cost nothing when disabled.
 */
internal object SdkLog {

    const val TAG = "DeviceSignals"

    @Volatile
    private var logger: SdkLogger? = null

    @Volatile
    private var level: LogLevel = LogLevel.NONE

    fun configure(logger: SdkLogger?, level: LogLevel) {
        this.logger = logger
        this.level = level
    }

    fun isEnabled(target: LogLevel): Boolean =
        logger != null && level != LogLevel.NONE && level.ordinal >= target.ordinal

    inline fun debug(message: () -> String) = log(LogLevel.DEBUG, message)
    inline fun warn(message: () -> String) = log(LogLevel.WARN, message)
    inline fun error(message: () -> String) = log(LogLevel.ERROR, message)

    inline fun log(target: LogLevel, message: () -> String) {
        if (isEnabled(target)) {
            logger?.log(target, TAG, message())
        }
    }

    /** Renders a module's collected data as `key=value, key=value` for DEBUG dumps. */
    fun formatFields(data: Map<String, Any?>): String =
        data.entries.joinToString(", ") { (k, v) -> "$k=${formatValue(v)}" }

    private fun formatValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> if (value.length > 48) "\"${value.take(45)}...\"" else "\"$value\""
        is Map<*, *> -> "{" + value.entries.joinToString(", ") { "${it.key}=${formatValue(it.value)}" } + "}"
        is List<*> -> "[" + value.joinToString(", ") { formatValue(it) } + "]"
        else -> value.toString()
    }
}
