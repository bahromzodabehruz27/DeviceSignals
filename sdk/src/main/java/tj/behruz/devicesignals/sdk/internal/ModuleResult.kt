package tj.behruz.devicesignals.sdk.internal

internal data class ModuleResult(
    val data: Map<String, Any?>,
    val missingFields: List<String>,
)
