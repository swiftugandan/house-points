package dev.housepoints.data

import dev.housepoints.contracts.DecodeResult
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpCodec
import java.io.ByteArrayOutputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

public sealed interface ExportResult {
    public data class Ok(val ops: List<Op>) : ExportResult
    /** The passphrase is wrong, or the file was altered: AES-GCM cannot tell the two apart. */
    public data object WrongPassphrase : ExportResult
    public data class Corrupt(val reason: String) : ExportResult
}

/**
 * Passphrase-encrypted export of the whole op log (SPEC FR-46, SAD §3.4).
 *
 * File: `"HPX1" ‖ salt(16) ‖ iterations u32 ‖ iv(12) ‖ AES-256-GCM(payload)`, where the key is
 * PBKDF2-HMAC-SHA256(passphrase, salt, iterations) [SAD R20] and the header is the associated data.
 * Payload: `count u32 ‖ (length u32 ‖ OpCodec envelope)*`.
 */
public object ExportCodec {
    public const val DEFAULT_ITERATIONS: Int = 600_000

    private val MAGIC = "HPX1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val MAX_ITERATIONS = 10_000_000
    private val HEADER_BYTES = MAGIC.size + SALT_BYTES + Int.SIZE_BYTES + IV_BYTES
    private val random = SecureRandom()

    public fun encrypt(ops: List<Op>, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).put(salt).putInt(iterations).put(iv).array()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, derive(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(payload(ops))
    }

    public fun decrypt(bytes: ByteArray, passphrase: CharArray): ExportResult {
        if (bytes.size < HEADER_BYTES || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            return ExportResult.Corrupt("not a House Points export")
        }
        val header = ByteBuffer.wrap(bytes, 0, HEADER_BYTES)
        header.position(MAGIC.size)
        val salt = ByteArray(SALT_BYTES).also { header.get(it) }
        val iterations = header.int
        val iv = ByteArray(IV_BYTES).also { header.get(it) }
        if (iterations !in 1..MAX_ITERATIONS) return ExportResult.Corrupt("implausible work factor $iterations")

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, derive(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(bytes, 0, HEADER_BYTES)
        val plain = try {
            cipher.doFinal(bytes, HEADER_BYTES, bytes.size - HEADER_BYTES)
        } catch (e: AEADBadTagException) {
            return ExportResult.WrongPassphrase
        }
        return readPayload(plain)
    }

    private fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun payload(ops: List<Op>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(ops.size).array())
        ops.forEach { op ->
            val encoded = OpCodec.encode(op)
            out.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(encoded.size).array())
            out.write(encoded)
        }
        return out.toByteArray()
    }

    private fun readPayload(plain: ByteArray): ExportResult {
        val buffer = ByteBuffer.wrap(plain)
        return try {
            val count = buffer.int
            if (count < 0) return ExportResult.Corrupt("negative op count")
            val ops = ArrayList<Op>()
            repeat(count) {
                val length = buffer.int
                if (length < 0 || length > buffer.remaining()) return ExportResult.Corrupt("bad op length")
                when (val decoded = OpCodec.decode(ByteArray(length).also { buffer.get(it) })) {
                    is DecodeResult.Decoded -> ops += decoded.op
                    is DecodeResult.Malformed -> return ExportResult.Corrupt(decoded.reason)
                }
            }
            if (buffer.hasRemaining()) ExportResult.Corrupt("trailing bytes") else ExportResult.Ok(ops)
        } catch (e: BufferUnderflowException) {
            ExportResult.Corrupt("truncated export")
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
