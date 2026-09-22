package tj.behruz.devicesignals.sdk.internal.modules

import android.content.Context
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule
import tj.behruz.devicesignals.sdk.internal.cache.ModuleCache
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class PlayIntegrityModule(
    private val cloudProjectNumber: String,
) : SignalModule {
    override val module = Module.PLAY_INTEGRITY

    override suspend fun collect(context: CollectContext): ModuleResult {
        // Check cache first
        val cachedToken = ModuleCache.getCachedPlayIntegrityToken()
        if (cachedToken != null) {
            return ModuleResult(
                data = mapOf("token" to cachedToken),
                missingFields = emptyList(),
            )
        }

        // Try to use Play Integrity via reflection
        return try {
            val token = requestIntegrityToken(context.appContext)
            if (token != null) {
                ModuleCache.cachePlayIntegrityToken(token)
                ModuleResult(
                    data = mapOf("token" to token),
                    missingFields = emptyList(),
                )
            } else {
                ModuleResult(
                    data = mapOf<String, Any?>("token" to null),
                    missingFields = listOf("token"),
                )
            }
        } catch (_: Exception) {
            ModuleResult(
                data = mapOf<String, Any?>("token" to null),
                missingFields = listOf("token"),
            )
        }
    }

    private suspend fun requestIntegrityToken(context: Context): String? {
        return try {
            // Use reflection to avoid hard dependency on Play Integrity
            val factoryClass = Class.forName("com.google.android.play.core.integrity.IntegrityManagerFactory")
            val createMethod = factoryClass.getMethod("create", Context::class.java)
            val manager = createMethod.invoke(null, context)

            val requestBuilderClass = Class.forName("com.google.android.play.core.integrity.IntegrityTokenRequest\$Builder")
            val builder = requestBuilderClass.getDeclaredConstructor().newInstance()
            val setCloudProjectMethod = requestBuilderClass.getMethod("setCloudProjectNumber", Long::class.java)
            setCloudProjectMethod.invoke(builder, cloudProjectNumber.toLong())
            val buildMethod = requestBuilderClass.getMethod("build")
            val request = buildMethod.invoke(builder)

            val managerClass = Class.forName("com.google.android.play.core.integrity.IntegrityManager")
            val requestTokenMethod = managerClass.getMethod("requestIntegrityToken", request.javaClass.interfaces.firstOrNull() ?: request.javaClass)

            val task = requestTokenMethod.invoke(manager, request)

            // Await the Task result
            suspendCancellableCoroutine { cont ->
                val taskClass = task.javaClass
                val addOnSuccessMethod = taskClass.getMethod("addOnSuccessListener", Class.forName("com.google.android.gms.tasks.OnSuccessListener"))
                val addOnFailureMethod = taskClass.getMethod("addOnFailureListener", Class.forName("com.google.android.gms.tasks.OnFailureListener"))

                val successProxy = java.lang.reflect.Proxy.newProxyInstance(
                    javaClass.classLoader,
                    arrayOf(Class.forName("com.google.android.gms.tasks.OnSuccessListener"))
                ) { _, _, args ->
                    val response = args[0]
                    val tokenMethod = response.javaClass.getMethod("token")
                    val token = tokenMethod.invoke(response) as? String
                    cont.resume(token)
                    null
                }

                val failureProxy = java.lang.reflect.Proxy.newProxyInstance(
                    javaClass.classLoader,
                    arrayOf(Class.forName("com.google.android.gms.tasks.OnFailureListener"))
                ) { _, _, _ ->
                    cont.resume(null)
                    null
                }

                addOnSuccessMethod.invoke(task, successProxy)
                addOnFailureMethod.invoke(task, failureProxy)
            }
        } catch (_: Exception) {
            null
        }
    }
}
