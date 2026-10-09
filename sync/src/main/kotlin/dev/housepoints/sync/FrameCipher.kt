package dev.housepoints.sync

import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals and opens post-authentication frames with AES-256-GCM [SAD R18].
 *
 * Nonce = direction byte ‖ three zero bytes ‖ 64-bit counter. Counters are implicit and per direction,
 * so a replayed, reordered, dropped or reflected frame fails authentication. The frame header (type and
 * ciphertext length) is the associated data. Sealing and opening use separate state, so one coroutine may
 * send while another receives.
 */
internal class FrameCipher(key: ByteArray, private val sendDirection: Byte, private val receiveDirection: Byte) {
    private val secret = SecretKeySpec(key, "AES")
    private val sealer = Cipher.getInstance(TRANSFORMATION)
    private val opener = Cipher.getInstance(TRANSFORMATION)
    private var sendCounter = 0L
    private var receiveCounter = 0L

    fun seal(type: Byte, body: ByteArray): ByteArray {
        val header = Wire.header(type, body.size + TAG_BYTES)
        sealer.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, nonce(sendDirection, sendCounter)))
        sealer.updateAAD(header)
        val sealed = sealer.doFinal(body)
        sendCounter++
        return header + sealed
    }

    /** Returns the frame's type and plaintext body; throws [AEADBadTagException] when tampered. */
    fun open(frame: ByteArray): Pair<Byte, ByteArray> {
        val (type, sealed) = Wire.parse(frame) ?: throw AEADBadTagException("malformed frame")
        opener.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, nonce(receiveDirection, receiveCounter)))
        opener.updateAAD(frame, 0, Wire.HEADER_BYTES)
        val body = opener.doFinal(sealed)
        receiveCounter++
        return type to body
    }

    private fun nonce(direction: Byte, counter: Long): ByteArray =
        ByteBuffer.allocate(NONCE_BYTES).put(direction).put(ByteArray(PADDING_BYTES)).putLong(counter).array()

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        const val NONCE_BYTES = 12
        const val PADDING_BYTES = 3
    }
}
