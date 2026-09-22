package tj.behruz.devicesignals.sdk.internal.cache

import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import java.util.concurrent.ConcurrentHashMap

internal object ModuleCache {

    private val staticCache = ConcurrentHashMap<Module, ModuleResult>()

    @Volatile
    private var playIntegrityToken: String? = null

    @Volatile
    private var playIntegrityTokenTimestamp: Long = 0L

    private const val PLAY_INTEGRITY_TTL_MS = 5 * 60 * 1000L // 5 minutes

    fun reset() {
        staticCache.clear()
        playIntegrityToken = null
        playIntegrityTokenTimestamp = 0L
    }

    suspend fun getOrCollect(
        module: Module,
        collector: suspend () -> ModuleResult,
    ): ModuleResult {
        if (module.isStatic) {
            staticCache[module]?.let { return it }
            val result = collector()
            staticCache[module] = result
            return result
        }
        return collector()
    }

    fun getCachedPlayIntegrityToken(): String? {
        val token = playIntegrityToken ?: return null
        val elapsed = System.currentTimeMillis() - playIntegrityTokenTimestamp
        if (elapsed > PLAY_INTEGRITY_TTL_MS) {
            playIntegrityToken = null
            return null
        }
        return token
    }

    fun cachePlayIntegrityToken(token: String) {
        playIntegrityToken = token
        playIntegrityTokenTimestamp = System.currentTimeMillis()
    }
}
