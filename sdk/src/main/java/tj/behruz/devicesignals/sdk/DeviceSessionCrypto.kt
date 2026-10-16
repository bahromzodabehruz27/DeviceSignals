package tj.behruz.devicesignals.sdk

import android.util.Base64
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import tj.behruz.devicesignals.sdk.internal.SealedBoxCrypto
import java.security.SecureRandom

/**
 * Helpers for testing and debugging encrypted device sessions on-device.
 *
 * In production the private key lives only on your backend and sessions are opened there with
 * libsodium's `crypto_box_seal_open`. Use this object to generate a throwaway keypair in a demo /
 * QA build and inspect what [DeviceSdk.buildDeviceSession] actually sends.
 */
object DeviceSessionCrypto {

    data class KeyPair(
        val publicKeyBase64: String,
        val privateKeyBase64: String,
    )

    /** Generates a fresh X25519 keypair, both halves base64-encoded (32 bytes each). */
    @JvmStatic
    fun generateKeyPair(): KeyPair {
        val kpg = X25519KeyPairGenerator()
        kpg.init(X25519KeyGenerationParameters(SecureRandom()))
        val kp = kpg.generateKeyPair()
        val pk = (kp.public as X25519PublicKeyParameters).encoded
        val sk = (kp.private as X25519PrivateKeyParameters).encoded
        return KeyPair(
            publicKeyBase64 = Base64.encodeToString(pk, Base64.NO_WRAP),
            privateKeyBase64 = Base64.encodeToString(sk, Base64.NO_WRAP),
        )
    }

    /**
     * Opens a sealed session produced by [DeviceSdk.buildDeviceSession] and returns the JSON inside.
     *
     * @throws DeviceSdkException.InvalidArgumentException if a key has the wrong size or the payload is too short
     * @throws DeviceSdkException.EncryptionException if the MAC does not verify (wrong key or corrupted data)
     */
    @JvmStatic
    fun open(
        sealedBase64: String,
        publicKeyBase64: String,
        privateKeyBase64: String,
    ): String {
        val pk = SealedBoxCrypto.decodeAndValidatePublicKey(publicKeyBase64)
        val sk = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
        if (sk.size != 32) {
            throw DeviceSdkException.InvalidArgumentException("privateKey must be 32 bytes, got ${sk.size}")
        }
        val sealed = Base64.decode(sealedBase64, Base64.NO_WRAP)
        if (sealed.size < SealedBoxCrypto.SEALBYTES) {
            throw DeviceSdkException.InvalidArgumentException(
                "sealed payload must be at least ${SealedBoxCrypto.SEALBYTES} bytes, got ${sealed.size}"
            )
        }
        return String(SealedBoxCrypto.sealOpen(sealed, pk, sk), Charsets.UTF_8)
    }
}
