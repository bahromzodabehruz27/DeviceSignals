package tj.behruz.devicesignals.sdk

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
