package tj.behruz.devicesignals.sdk.internal

import android.util.Base64
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import tj.behruz.devicesignals.sdk.DeviceSdkException
import java.security.MessageDigest
import java.security.SecureRandom

internal object SealedBoxCrypto {

    private const val CRYPTO_BOX_PUBLICKEYBYTES = 32
    private const val POLY1305_TAG_SIZE = 16
    const val SEALBYTES = 32 + POLY1305_TAG_SIZE // ephemeral_pk(32) + poly1305_tag(16)

    fun decodeAndValidatePublicKey(base64Key: String): ByteArray {
        val key = Base64.decode(base64Key, Base64.NO_WRAP)
        if (key.size != CRYPTO_BOX_PUBLICKEYBYTES) {
            throw DeviceSdkException.InvalidArgumentException(
                "fraudPublicKey must be $CRYPTO_BOX_PUBLICKEYBYTES bytes, got ${key.size}"
            )
        }
        return key
    }

    fun seal(plaintext: ByteArray, recipientPk: ByteArray): ByteArray {
        // 1. Generate ephemeral X25519 keypair
        val kpg = X25519KeyPairGenerator()
        kpg.init(X25519KeyGenerationParameters(SecureRandom()))
        val kp = kpg.generateKeyPair()
        val ephPk = (kp.public as X25519PublicKeyParameters).encoded
        val ephSk = kp.private as X25519PrivateKeyParameters

        // 2. X25519 DH → shared secret
        val agreement = X25519Agreement()
        agreement.init(ephSk)
        val shared = ByteArray(32)
        agreement.calculateAgreement(X25519PublicKeyParameters(recipientPk, 0), shared, 0)

        // 3. NaCl key derivation: k = HSalsa20(shared, 0^16)
        val boxKey = hSalsa20(shared, ByteArray(16))

        // 4. Nonce = Blake2b(ephPk || recipientPk, outputLen=24)
        val nonce = blake2b24(ephPk, recipientPk)

        // 5. Encrypt: NaCl secretbox (XSalsa20-Poly1305)
        val box = secretBox(plaintext, nonce, boxKey)

        // 6. Return: ephPk(32) || box(16 + plaintext.size)
        return ephPk + box
    }

    fun sealOpen(ciphertext: ByteArray, recipientPk: ByteArray, recipientSk: ByteArray): ByteArray {
        require(ciphertext.size >= SEALBYTES)
        val ephPk = ciphertext.copyOfRange(0, 32)
        val box = ciphertext.copyOfRange(32, ciphertext.size)

        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(recipientSk, 0))
        val shared = ByteArray(32)
        agreement.calculateAgreement(X25519PublicKeyParameters(ephPk, 0), shared, 0)

        val boxKey = hSalsa20(shared, ByteArray(16))
        val nonce = blake2b24(ephPk, recipientPk)

        return secretBoxOpen(box, nonce, boxKey)
    }

    // --- NaCl crypto_secretbox: XSalsa20-Poly1305 ---

    // Exposed for known-answer testing against libsodium vectors
    internal fun secretBoxForTest(message: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray =
        secretBox(message, nonce, key)

    internal fun secretBoxOpenForTest(box: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray =
        secretBoxOpen(box, nonce, key)

    internal fun hSalsa20ForTest(key: ByteArray, input: ByteArray): ByteArray =
        hSalsa20(key, input)

    private fun secretBox(message: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        val engine = XSalsa20Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))

        // First 32 bytes of keystream → Poly1305 one-time key
        val poly1305Key = ByteArray(32)
        engine.processBytes(ByteArray(32), 0, 32, poly1305Key, 0)

        // Encrypt message
        val encrypted = ByteArray(message.size)
        engine.processBytes(message, 0, message.size, encrypted, 0)

        // Poly1305 MAC over ciphertext
        val mac = Poly1305()
        mac.init(KeyParameter(poly1305Key))
        mac.update(encrypted, 0, encrypted.size)
        val tag = ByteArray(POLY1305_TAG_SIZE)
        mac.doFinal(tag, 0)

        // NaCl format: tag(16) || ciphertext
        return tag + encrypted
    }

    private fun secretBoxOpen(box: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        require(box.size >= POLY1305_TAG_SIZE)
        val tag = box.copyOfRange(0, POLY1305_TAG_SIZE)
        val encrypted = box.copyOfRange(POLY1305_TAG_SIZE, box.size)

        val engine = XSalsa20Engine()
        engine.init(false, ParametersWithIV(KeyParameter(key), nonce))

        val poly1305Key = ByteArray(32)
        engine.processBytes(ByteArray(32), 0, 32, poly1305Key, 0)

        // Verify MAC
        val mac = Poly1305()
        mac.init(KeyParameter(poly1305Key))
        mac.update(encrypted, 0, encrypted.size)
        val computedTag = ByteArray(POLY1305_TAG_SIZE)
        mac.doFinal(computedTag, 0)

        if (!MessageDigest.isEqual(tag, computedTag)) {
            throw DeviceSdkException.EncryptionException("MAC verification failed")
        }

        // Decrypt
        val decrypted = ByteArray(encrypted.size)
        engine.processBytes(encrypted, 0, encrypted.size, decrypted, 0)
        return decrypted
    }

    // --- Blake2b with 24-byte output (for sealed box nonce) ---

    private fun blake2b24(a: ByteArray, b: ByteArray): ByteArray {
        val digest = Blake2bDigest(192) // 24 bytes = 192 bits
        digest.update(a, 0, a.size)
        digest.update(b, 0, b.size)
        val out = ByteArray(24)
        digest.doFinal(out, 0)
        return out
    }

    // --- HSalsa20: derives NaCl box key from DH shared secret ---

    private fun hSalsa20(key: ByteArray, input: ByteArray): ByteArray {
        val sigma = intArrayOf(0x61707865, 0x3320646e, 0x79622d32, 0x6b206574)

        val x = IntArray(16)
        x[0]  = sigma[0]
        x[1]  = littleEndianToInt(key, 0)
        x[2]  = littleEndianToInt(key, 4)
        x[3]  = littleEndianToInt(key, 8)
        x[4]  = littleEndianToInt(key, 12)
        x[5]  = sigma[1]
        x[6]  = littleEndianToInt(input, 0)
        x[7]  = littleEndianToInt(input, 4)
        x[8]  = littleEndianToInt(input, 8)
        x[9]  = littleEndianToInt(input, 12)
        x[10] = sigma[2]
        x[11] = littleEndianToInt(key, 16)
        x[12] = littleEndianToInt(key, 20)
        x[13] = littleEndianToInt(key, 24)
        x[14] = littleEndianToInt(key, 28)
        x[15] = sigma[3]

        // 20 rounds (10 double-rounds)
        repeat(10) {
            // Column round
            quarterRound(x, 0, 4, 8, 12)
            quarterRound(x, 5, 9, 13, 1)
            quarterRound(x, 10, 14, 2, 6)
            quarterRound(x, 15, 3, 7, 11)
            // Row round
            quarterRound(x, 0, 1, 2, 3)
            quarterRound(x, 5, 6, 7, 4)
            quarterRound(x, 10, 11, 8, 9)
            quarterRound(x, 15, 12, 13, 14)
        }

        // Output: x[0], x[5], x[10], x[15], x[6], x[7], x[8], x[9]
        val out = ByteArray(32)
        intToLittleEndian(x[0], out, 0)
        intToLittleEndian(x[5], out, 4)
        intToLittleEndian(x[10], out, 8)
        intToLittleEndian(x[15], out, 12)
        intToLittleEndian(x[6], out, 16)
        intToLittleEndian(x[7], out, 20)
        intToLittleEndian(x[8], out, 24)
        intToLittleEndian(x[9], out, 28)
        return out
    }

    private fun quarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[b] = x[b] xor ((x[a] + x[d]).rotateLeft(7))
        x[c] = x[c] xor ((x[b] + x[a]).rotateLeft(9))
        x[d] = x[d] xor ((x[c] + x[b]).rotateLeft(13))
        x[a] = x[a] xor ((x[d] + x[c]).rotateLeft(18))
    }

    private fun littleEndianToInt(bs: ByteArray, off: Int): Int =
        (bs[off].toInt() and 0xff) or
        ((bs[off + 1].toInt() and 0xff) shl 8) or
        ((bs[off + 2].toInt() and 0xff) shl 16) or
        ((bs[off + 3].toInt() and 0xff) shl 24)

    private fun intToLittleEndian(n: Int, bs: ByteArray, off: Int) {
        bs[off]     = n.toByte()
        bs[off + 1] = (n ushr 8).toByte()
        bs[off + 2] = (n ushr 16).toByte()
        bs[off + 3] = (n ushr 24).toByte()
    }
}
