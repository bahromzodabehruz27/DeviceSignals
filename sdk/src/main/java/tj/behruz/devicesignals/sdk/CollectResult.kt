package tj.behruz.devicesignals.sdk

import tj.behruz.devicesignals.sdk.internal.JsonPayloadBuilder

data class CollectResult(
    val status: CollectStatus,
    val moduleData: Map<Module, Map<String, Any?>>,
    val missingFields: List<String>,
    val durationMs: Long,
    internal val sessionId: String,
    internal val visitorId: String,
) {
    fun toJson(): String = JsonPayloadBuilder.build(
        sessionId = sessionId,
        visitorId = visitorId,
        status = status,
        durationMs = durationMs,
        moduleData = moduleData,
        missingFields = missingFields,
    )
}
