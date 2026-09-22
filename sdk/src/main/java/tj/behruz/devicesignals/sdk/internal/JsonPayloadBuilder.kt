package tj.behruz.devicesignals.sdk.internal

import org.json.JSONArray
import org.json.JSONObject
import tj.behruz.devicesignals.sdk.CollectStatus
import tj.behruz.devicesignals.sdk.Module
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal object JsonPayloadBuilder {

    fun build(
        sessionId: String,
        visitorId: String,
        status: CollectStatus,
        durationMs: Long,
        moduleData: Map<Module, Map<String, Any?>>,
        missingFields: List<String>,
    ): String {
        val root = JSONObject()
        root.put("schema_version", "1.0")
        root.put("id", UUID.randomUUID().toString())
        root.put("session_id", sessionId)
        root.put("visitor_id", visitorId)
        root.put("collected_at", formatIso8601())
        root.put("platform", "android")

        val collection = JSONObject()
        collection.put("status", status.name)
        collection.put("duration_ms", durationMs)
        collection.put("modules_collected", JSONArray(moduleData.keys.map { it.jsonKey }))
        collection.put("missing_fields", JSONArray(missingFields))
        root.put("collection", collection)

        for ((module, data) in moduleData) {
            root.put(module.jsonKey, mapToJson(data))
        }

        return root.toString(2)
    }

    private fun mapToJson(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        for ((key, value) in map) {
            when (value) {
                null -> obj.put(key, JSONObject.NULL)
                is List<*> -> obj.put(key, JSONArray(value))
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    obj.put(key, mapToJson(value as Map<String, Any?>))
                }
                else -> obj.put(key, value)
            }
        }
        return obj
    }

    private fun formatIso8601(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
