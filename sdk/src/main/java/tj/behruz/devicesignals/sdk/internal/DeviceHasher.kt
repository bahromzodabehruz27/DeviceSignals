package tj.behruz.devicesignals.sdk.internal

import java.security.MessageDigest

internal object DeviceHasher {

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun computeHash(
        manufacturer: String,
        model: String,
        appGuid: String,
        deviceId: String,
    ): String {
        return sha256("$manufacturer|$model|$appGuid|$deviceId")
    }
}
