package dev.housepoints.sync

import java.io.ByteArrayOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 5869 HKDF with HMAC-SHA256 [SAD R17]. */
internal object Hkdf {
    private const val ALGORITHM = "HmacSHA256"
    private const val HASH_BYTES = 32

    /** [length] must not exceed 255 × 32 bytes (RFC 5869 §2.3); every caller asks for 64. */
    fun derive(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(HASH_BYTES) else salt, ALGORITHM))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, ALGORITHM))
        val okm = ByteArrayOutputStream(length + HASH_BYTES)
        var block = ByteArray(0)
        var counter = 1
        while (okm.size() < length) {
            mac.update(block)
            mac.update(info)
            mac.update(counter.toByte())
            block = mac.doFinal()
            okm.write(block)
            counter++
        }
        return okm.toByteArray().copyOf(length)
    }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        parts.forEach(mac::update)
        return mac.doFinal()
    }
}
