package tj.behruz.devicesignals.sdk

import android.util.Log

/**
 * Ready-made [SdkLogger] that writes to Android logcat under the `DeviceSignals` tag.
 *
 * ```kotlin
 * DeviceSdk.init(context, SdkConfig(logLevel = LogLevel.DEBUG, logger = LogcatLogger))
 * ```
 */
object LogcatLogger : SdkLogger {
    override fun log(level: LogLevel, tag: String, message: String) {
        when (level) {
            LogLevel.ERROR -> Log.e(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.NONE -> Unit
        }
    }
}
