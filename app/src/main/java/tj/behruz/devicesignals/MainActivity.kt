package tj.behruz.devicesignals

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import tj.behruz.devicesignals.sdk.CollectResult
import tj.behruz.devicesignals.sdk.CollectStatus
import tj.behruz.devicesignals.sdk.DeviceSdk
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.SdkConfig
import tj.behruz.devicesignals.ui.theme.DeviceSignalsTheme

private const val DEMO_FRAUD_PUBLIC_KEY = "HLDZQov2k4syHBXdVmZ0uLiMsSMMI2oxAgWprt/9znA="

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        DeviceSdk.init(
            this,
            SdkConfig(
                deviceId = "demo-device-id-001",
                fraudPublicKeyBase64 = DEMO_FRAUD_PUBLIC_KEY,
            ),
        )
        DeviceSdk.attach(this)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        )

        setContent {
            DeviceSignalsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DemoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var sessionId by remember { mutableStateOf("demo-session-1") }
    var visitorId by remember { mutableStateOf("demo-visitor-1") }
    val moduleChecks = remember {
        mutableStateMapOf<Module, Boolean>().apply {
            Module.ALL.forEach { put(it, true) }
        }
    }
    var collectResult by remember { mutableStateOf<CollectResult?>(null) }
    var statusText by remember { mutableStateOf("Ready") }
    var collecting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var deviceSessionBase64 by remember { mutableStateOf<String?>(null) }
    var buildingSession by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "Device Signals SDK Demo",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            OutlinedTextField(
                value = sessionId,
                onValueChange = { sessionId = it },
                label = { Text("Session ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        item {
            OutlinedTextField(
                value = visitorId,
                onValueChange = { visitorId = it },
                label = { Text("Visitor ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        item {
            Text("Modules:", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Module.entries.forEach { module ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = moduleChecks[module] == true,
                            onCheckedChange = { moduleChecks[module] = it },
                        )
                        Text(module.name, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    collecting = true
                    statusText = "Collecting..."
                    collectResult = null
                    errorText = null
                    val selected = moduleChecks.filter { it.value }.keys.toSet()
                    scope.launch {
                        try {
                            val result = DeviceSdk.collect(sessionId, visitorId, selected)
                            collectResult = result
                            statusText = when (result.status) {
                                CollectStatus.SUCCESS -> "SUCCESS (${result.durationMs}ms)"
                                CollectStatus.PARTIAL -> "PARTIAL (${result.durationMs}ms) — ${result.missingFields.size} missing"
                                CollectStatus.FAILED -> "FAILED (${result.durationMs}ms)"
                            }
                        } catch (e: Exception) {
                            statusText = "Error: ${e.message}"
                            errorText = e.stackTraceToString()
                        } finally {
                            collecting = false
                        }
                    }
                },
                enabled = !collecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (collecting) "Collecting..." else "Collect")
            }
        }

        item {
            Button(
                onClick = {
                    buildingSession = true
                    deviceSessionBase64 = null
                    errorText = null
                    scope.launch {
                        try {
                            val base64 = DeviceSdk.buildDeviceSession(sessionId, visitorId)
                            deviceSessionBase64 = base64
                            statusText = "Device session built (${base64.length} chars)"
                        } catch (e: Exception) {
                            statusText = "Error: ${e.message}"
                            errorText = e.stackTraceToString()
                        } finally {
                            buildingSession = false
                        }
                    }
                },
                enabled = !buildingSession && !collecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (buildingSession) "Building..." else "Build Device Session")
            }
        }

        if (deviceSessionBase64 != null) {
            item {
                val clipboardManager = LocalClipboardManager.current
                SectionCard(title = "Device Session (sealed base64)") {
                    Text(
                        text = deviceSessionBase64!!,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(deviceSessionBase64!!))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Copy")
                    }
                }
            }
        }

        item {
            Text(
                text = "Status: $statusText",
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    statusText.startsWith("SUCCESS") -> MaterialTheme.colorScheme.primary
                    statusText.startsWith("PARTIAL") -> MaterialTheme.colorScheme.tertiary
                    statusText.startsWith("FAILED") || statusText.startsWith("Error") -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }

        // Error card
        if (errorText != null) {
            item {
                SectionCard(title = "Error", isError = true) {
                    Text(
                        text = errorText!!,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // Result section cards
        val result = collectResult
        if (result != null) {
            val json = try {
                JSONObject(result.toJson())
            } catch (_: Exception) {
                null
            }

            if (json != null) {
                // Metadata card
                item {
                    SectionCard(title = "Metadata") {
                        JsonField("schema_version", json.optString("schema_version"))
                        JsonField("id", json.optString("id"))
                        JsonField("session_id", json.optString("session_id"))
                        JsonField("visitor_id", json.optString("visitor_id"))
                        JsonField("collected_at", json.optString("collected_at"))
                        JsonField("platform", json.optString("platform"))
                    }
                }

                // Collection summary card
                val collection = json.optJSONObject("collection")
                if (collection != null) {
                    item {
                        SectionCard(title = "Collection Summary") {
                            JsonField("status", collection.optString("status"))
                            JsonField("duration_ms", collection.opt("duration_ms")?.toString() ?: "")
                            val modulesCollected = collection.optJSONArray("modules_collected")
                            if (modulesCollected != null) {
                                JsonField("modules_collected", jsonArrayToString(modulesCollected))
                            }
                            val missing = collection.optJSONArray("missing_fields")
                            if (missing != null && missing.length() > 0) {
                                JsonField("missing_fields", jsonArrayToString(missing))
                            }
                        }
                    }
                }

                // One card per module section
                val metadataKeys = setOf("schema_version", "id", "session_id", "visitor_id", "collected_at", "platform", "collection")
                val moduleKeys = json.keys().asSequence().filter { it !in metadataKeys }.toList()

                items(moduleKeys) { key ->
                    val moduleJson = json.optJSONObject(key)
                    if (moduleJson != null) {
                        ModuleSectionCard(title = key, data = moduleJson)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionCard(
    title: String,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isError) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ModuleSectionCard(title: String, data: JSONObject) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${data.length()} fields  ${if (expanded) "▲" else "▼"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    val keys = data.keys().asSequence().toList()
                    keys.forEachIndexed { index, key ->
                        val value = data.opt(key)
                        val displayValue = when (value) {
                            is JSONArray -> jsonArrayToString(value)
                            JSONObject.NULL -> "null"
                            else -> value?.toString() ?: "null"
                        }
                        JsonField(key, displayValue)
                        if (index < keys.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonField(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = key,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.6f),
        )
    }
}

private fun jsonArrayToString(array: JSONArray): String {
    return (0 until array.length()).joinToString(", ") { array.opt(it)?.toString() ?: "null" }
}
