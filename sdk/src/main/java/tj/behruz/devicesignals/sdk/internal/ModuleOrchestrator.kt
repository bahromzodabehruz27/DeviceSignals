package tj.behruz.devicesignals.sdk.internal

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import tj.behruz.devicesignals.sdk.CollectResult
import tj.behruz.devicesignals.sdk.CollectStatus
import tj.behruz.devicesignals.sdk.LogLevel
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.ModuleStatus
import tj.behruz.devicesignals.sdk.SdkConfig
import tj.behruz.devicesignals.sdk.internal.cache.ModuleCache
import kotlin.time.Duration.Companion.milliseconds

internal class ModuleOrchestrator(
    private val modules: List<SignalModule>,
    private val config: SdkConfig,
) {
    suspend fun collect(
        collectContext: CollectContext,
        sessionId: String,
        visitorId: String,
        requestedModules: Set<Module>,
    ): CollectResult {
        val effectiveModules = computeEffectiveModules(requestedModules)
        val moduleMap = modules.associateBy { it.module }
        val skipped = requestedModules - effectiveModules
        val unregistered = effectiveModules.filter { it !in moduleMap }

        SdkLog.debug {
            "collect start: session=$sessionId visitor=$visitorId " +
                "modules=${effectiveModules.names()} " +
                (if (skipped.isNotEmpty()) "skipped(disabled)=${skipped.names()} " else "") +
                (if (unregistered.isNotEmpty()) "skipped(unregistered)=${unregistered.names()} " else "") +
                "timeout=${config.timeoutMs}ms moduleTimeout=${config.moduleTimeoutMs}ms"
        }

        val startTime = System.currentTimeMillis()
        val moduleData = mutableMapOf<Module, Map<String, Any?>>()
        val allMissingFields = mutableListOf<String>()

        try {
            withTimeout(config.timeoutMs.milliseconds) {
                coroutineScope {
                    val deferreds = effectiveModules.mapNotNull { mod ->
                        val impl = moduleMap[mod] ?: return@mapNotNull null
                        mod to async { collectModule(collectContext, impl) }
                    }

                    for ((mod, deferred) in deferreds) {
                        val result = try {
                            deferred.await()
                        } catch (_: Exception) {
                            null
                        }
                        if (result != null) {
                            moduleData[mod] = result.data
                            allMissingFields.addAll(
                                result.missingFields.map { "${mod.jsonKey}.$it" }
                            )
                        } else {
                            addAllFieldsAsMissing(mod, moduleMap[mod], allMissingFields)
                        }
                    }
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            val notCollected = effectiveModules.filter { it !in moduleData }
            SdkLog.warn {
                "collect timed out after ${config.timeoutMs}ms; " +
                    "collected=${moduleData.keys.names()} notCollected=${notCollected.names()}"
            }
            if (moduleData.isEmpty()) {
                val finishTime = System.currentTimeMillis()
                SdkLog.error { "collect FAILED: no module finished before the global timeout" }
                return CollectResult(
                    status = CollectStatus.FAILED,
                    moduleData = moduleData,
                    missingFields = allMissingFields,
                    durationMs = finishTime - startTime,
                    sessionId = sessionId,
                    visitorId = visitorId,
                    startedAtMs = startTime,
                    finishedAtMs = finishTime,
                )
            }
        }

        val finishTime = System.currentTimeMillis()
        val status = when {
            moduleData.isEmpty() -> CollectStatus.FAILED
            allMissingFields.isEmpty() -> CollectStatus.SUCCESS
            else -> CollectStatus.PARTIAL
        }

        SdkLog.log(if (status == CollectStatus.FAILED) LogLevel.ERROR else LogLevel.DEBUG) {
            "collect done: status=$status in ${finishTime - startTime}ms, " +
                "modules=${moduleData.size}/${effectiveModules.size} " +
                "fields=${moduleData.values.sumOf { it.size }} " +
                (if (allMissingFields.isEmpty()) "missing=none" else "missing=$allMissingFields")
        }

        return CollectResult(
            status = status,
            moduleData = moduleData,
            missingFields = allMissingFields,
            durationMs = finishTime - startTime,
            sessionId = sessionId,
            visitorId = visitorId,
            startedAtMs = startTime,
            finishedAtMs = finishTime,
        )
    }

    private suspend fun collectModule(
        context: CollectContext,
        impl: SignalModule,
    ): ModuleResult {
        val start = System.currentTimeMillis()
        var moduleStatus = ModuleStatus.SUCCESS
        try {
            val result = withTimeout(config.moduleTimeoutMs.milliseconds) {
                ModuleCache.getOrCollect(impl.module) {
                    impl.collect(context)
                }
            }
            val elapsed = System.currentTimeMillis() - start
            moduleStatus = if (result.missingFields.isEmpty()) ModuleStatus.SUCCESS else ModuleStatus.PARTIAL
            SdkLog.debug {
                "module ${impl.module.jsonKey}: $moduleStatus in ${elapsed}ms, " +
                    "${result.data.size} fields" +
                    (if (result.missingFields.isEmpty()) "" else ", missing=${result.missingFields}")
            }
            SdkLog.debug { "module ${impl.module.jsonKey} data: ${SdkLog.formatFields(result.data)}" }
            config.onModuleCompleted?.invoke(impl.module, elapsed, moduleStatus)
            return result
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            moduleStatus = ModuleStatus.FAILED
            SdkLog.error {
                "module ${impl.module.jsonKey}: FAILED in ${elapsed}ms: ${e.javaClass.simpleName}" +
                    (e.message?.let { ": $it" } ?: "")
            }
            config.onModuleCompleted?.invoke(impl.module, elapsed, moduleStatus)
            throw e
        }
    }

    private fun computeEffectiveModules(requestedModules: Set<Module>): Set<Module> {
        var effective = config.enabledModules.intersect(requestedModules)
        if (config.playIntegrityCloudProjectNumber == null) {
            effective = effective - Module.PLAY_INTEGRITY
        }
        return effective
    }

    private fun addAllFieldsAsMissing(
        mod: Module,
        impl: SignalModule?,
        missingFields: MutableList<String>,
    ) {
        // For failed modules, we add a generic missing field entry
        missingFields.add("${mod.jsonKey}.*")
    }

    private fun Iterable<Module>.names(): String = joinToString(",", "[", "]") { it.jsonKey }
}
