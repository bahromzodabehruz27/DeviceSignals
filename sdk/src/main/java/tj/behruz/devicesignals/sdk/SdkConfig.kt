package tj.behruz.devicesignals.sdk

data class SdkConfig(
    val timeoutMs: Long = 3_000L,
    val moduleTimeoutMs: Long = 1_500L,
    val enabledModules: Set<Module> = Module.ALL,
    val playIntegrityCloudProjectNumber: String? = null,
    val logLevel: LogLevel = LogLevel.NONE,
    val logger: SdkLogger? = null,
    val onModuleCompleted: ((Module, Long, ModuleStatus) -> Unit)? = null,
    val deviceId: String? = null,
    val fraudPublicKeyBase64: String? = null,
)
