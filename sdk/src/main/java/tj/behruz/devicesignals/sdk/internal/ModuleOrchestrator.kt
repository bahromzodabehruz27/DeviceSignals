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
import tj.behruz.devicesignals.sdk.SdkLogger
import tj.behruz.devicesignals.sdk.internal.cache.ModuleCache
import kotlin.time.Duration.Companion.milliseconds

internal class ModuleOrchestrator(
    private val modules: List<SignalModule>,
    private val config: SdkConfig,
) {
    private val logger: SdkLogger? get() = config.logger
    private val logLevel: LogLevel get() = config.logLevel

    suspend fun collect(
        collectContext: CollectContext,
        sessionId: String,
        visitorId: String,
        requestedModules: Set<Module>,
    ): CollectResult {
        val effectiveModules = computeEffectiveModules(requestedModules)
        val moduleMap = modules.associateBy { it.module }

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
            if (moduleData.isEmpty()) {
                val durationMs = System.currentTimeMillis() - startTime
                return CollectResult(
                    status = CollectStatus.FAILED,
                    moduleData = moduleData,
                    missingFields = allMissingFields,
                    durationMs = durationMs,
                    sessionId = sessionId,
                    visitorId = visitorId,
                )
            }
        }

        val durationMs = System.currentTimeMillis() - startTime
        val status = when {
            moduleData.isEmpty() -> CollectStatus.FAILED
            allMissingFields.isEmpty() -> CollectStatus.SUCCESS
            else -> CollectStatus.PARTIAL
        }

        return CollectResult(
            status = status,
            moduleData = moduleData,
            missingFields = allMissingFields,
            durationMs = durationMs,
            sessionId = sessionId,
            visitorId = visitorId,
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
            log(LogLevel.DEBUG, "Module ${impl.module.jsonKey} completed in ${elapsed}ms: $moduleStatus")
            config.onModuleCompleted?.invoke(impl.module, elapsed, moduleStatus)
            return result
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            moduleStatus = ModuleStatus.FAILED
            log(LogLevel.ERROR, "Module ${impl.module.jsonKey} failed in ${elapsed}ms: ${e.javaClass.simpleName}")
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

    private fun log(level: LogLevel, message: String) {
        if (logLevel != LogLevel.NONE && logLevel.ordinal >= level.ordinal) {
            logger?.log(level, TAG, message)
        }
    }

    companion object {
        private const val TAG = "DeviceSignals"
    }
}
