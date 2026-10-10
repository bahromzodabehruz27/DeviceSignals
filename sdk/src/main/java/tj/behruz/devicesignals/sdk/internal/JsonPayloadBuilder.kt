package tj.behruz.devicesignals.sdk.internal

import org.json.JSONArray
import org.json.JSONObject
import tj.behruz.devicesignals.sdk.BuildConfig
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
        startedAtMs: Long,
        finishedAtMs: Long,
    ): String {
        val mapped = SpecPayloadMapper.map(moduleData, status, BuildConfig.SDK_VERSION)

        val root = JSONObject()
        root.put("schema_version", "2.0")
        root.put("id", UUID.randomUUID().toString())
        root.put("session_id", sessionId)
        root.put("visitor_id", visitorId)
        root.put("collected_at", formatIso8601(Date()))
        root.put("first_open", formatIso8601(Date(startedAtMs)))
        root.put("last_open", formatIso8601(Date(finishedAtMs)))
        root.put("collection_duration_ms", durationMs)

        val fields = mapToJson(mapped.fields)
        for (key in fields.keys()) {
            root.put(key, fields.get(key))
        }
        root.put("collection_status", mapped.collectionStatus.name)
        root.put("missing_fields", JSONArray(mapped.missingFields))

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

    private fun formatIso8601(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
