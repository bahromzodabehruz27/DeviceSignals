package tj.behruz.devicesignals.sdk

interface SdkLogger {
    fun log(level: LogLevel, tag: String, message: String)
}
