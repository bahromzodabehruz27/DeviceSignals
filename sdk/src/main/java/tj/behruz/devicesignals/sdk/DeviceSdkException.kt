package tj.behruz.devicesignals.sdk

open class DeviceSdkException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    class NotInitializedException(
        message: String = "DeviceSdk.init() must be called before collect()",
    ) : DeviceSdkException(message)

    class InvalidArgumentException(
        message: String,
    ) : DeviceSdkException(message)

    class EncryptionException(
        message: String,
        cause: Throwable? = null,
    ) : DeviceSdkException(message, cause)
}
