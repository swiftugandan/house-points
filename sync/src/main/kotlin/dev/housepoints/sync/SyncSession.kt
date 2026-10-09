package dev.housepoints.sync

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Uuids
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * One authenticated, encrypted exchange of missing ops with one peer (SAD §4.3).
 *
 * 1. Both sides send HELLO (protocol, family, device, 32-byte nonce) in the clear and check the peer's.
 * 2. Keys come from HKDF-SHA256 over the family key, salted with both nonces in device order. Each side
 *    proves it holds the family key with an HMAC over both HELLOs and its own device id.
 * 3. Every later frame is AES-256-GCM sealed. Each side sends its version vector, then every op the
 *    peer lacks in batches, then DONE. Sending and receiving run concurrently, so neither side waits on
 *    the other's buffer.
 *
 * Received batches are appended as they arrive, so an interrupted session keeps what it got and the next
 * session resumes from the new vector. An op claiming another family after authentication means the
 * peer is broken or hostile: the session is rejected with [RejectReason.PROTOCOL_ERROR], and batches
 * already stored (which were all valid) stay. A session is single-use and always closes [transport].
 */
public class SyncSession(
    private val familyId: FamilyId,
    private val key: FamilyKey,
    private val self: DeviceId,
    private val log: OpLog,
    private val transport: Transport,
    private val clock: () -> InstantMs,
    private val receiveTimeoutMs: Long = DEFAULT_RECEIVE_TIMEOUT_MS,
) {
    private val received = AtomicInteger(0)

    public suspend fun run(): SyncOutcome {
        val startedAt = clock()
        val outcome = try {
            exchange(startedAt)
        } catch (e: IOException) {
            SyncOutcome.Interrupted(e.message ?: "link closed", received.get())
        }
        transport.close()
        return outcome
    }

    private suspend fun exchange(startedAt: InstantMs): SyncOutcome {
        val nonce = ByteArray(Wire.NONCE_BYTES).also(random::nextBytes)
        val ownHello = Wire.frame(Wire.HELLO, Wire.hello(Wire.Hello(PROTOCOL, familyId, self, nonce)))
        transport.send(ownHello)

        val peerHelloFrame = receive() ?: return interrupted("no HELLO from peer")
        val peerHelloBody = expect(peerHelloFrame, Wire.HELLO) ?: return rejected(RejectReason.PROTOCOL_ERROR, "expected HELLO")
        val peer = Wire.readHello(peerHelloBody) ?: return rejected(RejectReason.PROTOCOL_ERROR, "unreadable HELLO")
        when {
            peer.protocol != PROTOCOL -> return rejected(RejectReason.PROTOCOL_VERSION, "peer speaks protocol ${peer.protocol}")
            peer.family != familyId -> return rejected(RejectReason.WRONG_FAMILY, "peer belongs to ${peer.family}")
            peer.device == self -> return rejected(RejectReason.PROTOCOL_ERROR, "peer claims this device's identity")
        }

        val selfIsLow = Uuids.compare(self.uuid, peer.device.uuid) < 0
        val transcript = if (selfIsLow) ownHello + peerHelloFrame else peerHelloFrame + ownHello
        val salt = if (selfIsLow) nonce + peer.nonce else peer.nonce + nonce
        val keys = Hkdf.derive(key.bytes(), salt, INFO, 2 * KEY_BYTES)
        val macKey = keys.copyOfRange(0, KEY_BYTES)
        val encryptionKey = keys.copyOfRange(KEY_BYTES, 2 * KEY_BYTES)

        transport.send(Wire.frame(Wire.AUTH, proof(macKey, transcript, self)))
        val peerAuthFrame = receive() ?: return interrupted("no AUTH from peer")
        val peerProof = expect(peerAuthFrame, Wire.AUTH) ?: return rejected(RejectReason.PROTOCOL_ERROR, "expected AUTH")
        if (!MessageDigest.isEqual(peerProof, proof(macKey, transcript, peer.device))) {
            return rejected(RejectReason.AUTHENTICATION_FAILED, "peer does not hold this family's key")
        }

        val cipher = if (selfIsLow) {
            FrameCipher(encryptionKey, sendDirection = LOW_DIRECTION, receiveDirection = HIGH_DIRECTION)
        } else {
            FrameCipher(encryptionKey, sendDirection = HIGH_DIRECTION, receiveDirection = LOW_DIRECTION)
        }
        return exchangeOps(cipher, peer.device, startedAt)
    }

    private suspend fun exchangeOps(cipher: FrameCipher, peer: DeviceId, startedAt: InstantMs): SyncOutcome {
        transport.send(cipher.seal(Wire.VECTOR, Wire.vector(log.vector())))
        val first = receiveSealed(cipher)
        val vectorBody = when (first) {
            is Sealed.Frame -> if (first.type == Wire.VECTOR) first.body else return rejected(RejectReason.PROTOCOL_ERROR, "expected VECTOR")
            is Sealed.Failure -> return first.outcome
        }
        val peerVector = Wire.readVector(vectorBody) ?: return rejected(RejectReason.PROTOCOL_ERROR, "unreadable VECTOR")

        return coroutineScope {
            val sending = async { sendMissing(cipher, peerVector) }
            val failure = receiveUntilDone(cipher)
            if (failure != null) {
                sending.cancel()
                failure
            } else {
                SyncOutcome.Completed(peer, sending.await(), received.get(), startedAt, clock())
            }
        }
    }

    private suspend fun sendMissing(cipher: FrameCipher, peerVector: VersionVector): Int {
        val missing = log.opsAfter(peerVector)
        Wire.opsBatches(missing).forEach { batch -> transport.send(cipher.seal(Wire.OPS, batch)) }
        transport.send(cipher.seal(Wire.DONE, Wire.done(missing.size)))
        return missing.size
    }

    /** Null when the peer's DONE arrived; otherwise the outcome that ended the session. */
    private suspend fun receiveUntilDone(cipher: FrameCipher): SyncOutcome? {
        while (true) {
            val frame = when (val next = receiveSealed(cipher)) {
                is Sealed.Frame -> next
                is Sealed.Failure -> return next.outcome
            }
            when (frame.type) {
                Wire.OPS -> {
                    val ops = Wire.readOps(frame.body) ?: return rejected(RejectReason.PROTOCOL_ERROR, "unreadable OPS")
                    if (ops.any { it.familyId != familyId }) {
                        return rejected(RejectReason.PROTOCOL_ERROR, "peer sent ops from another family")
                    }
                    received.addAndGet(log.append(ops).added)
                }
                Wire.DONE -> return if (Wire.readDone(frame.body) != null) null else rejected(RejectReason.PROTOCOL_ERROR, "unreadable DONE")
                else -> return rejected(RejectReason.PROTOCOL_ERROR, "unexpected frame type ${frame.type}")
            }
        }
    }

    private sealed interface Sealed {
        class Frame(val type: Byte, val body: ByteArray) : Sealed
        class Failure(val outcome: SyncOutcome) : Sealed
    }

    private suspend fun receiveSealed(cipher: FrameCipher): Sealed {
        val frame = receive() ?: return Sealed.Failure(interrupted("link closed mid-session"))
        return try {
            val (type, body) = cipher.open(frame)
            Sealed.Frame(type, body)
        } catch (e: GeneralSecurityException) {
            Sealed.Failure(rejected(RejectReason.TAMPERED, "frame failed authentication"))
        }
    }

    private suspend fun receive(): ByteArray? = withTimeoutOrNull(receiveTimeoutMs) { transport.receive() }

    private fun expect(frame: ByteArray, type: Byte): ByteArray? =
        Wire.parse(frame)?.takeIf { it.first == type }?.second

    private fun proof(macKey: ByteArray, transcript: ByteArray, device: DeviceId): ByteArray =
        Hkdf.hmac(macKey, AUTH_LABEL, transcript, Uuids.toBytes(device.uuid))

    private fun rejected(reason: RejectReason, detail: String) = SyncOutcome.Rejected(reason, detail)

    private fun interrupted(detail: String) = SyncOutcome.Interrupted(detail, received.get())

    public companion object {
        public const val DEFAULT_RECEIVE_TIMEOUT_MS: Long = 30_000
        public const val PROTOCOL: Int = 1

        private const val KEY_BYTES = 32
        private const val LOW_DIRECTION: Byte = 0x01
        private const val HIGH_DIRECTION: Byte = 0x02
        private val INFO = "hp/sync/v1".toByteArray(Charsets.US_ASCII)
        private val AUTH_LABEL = "auth".toByteArray(Charsets.US_ASCII)
        private val random = SecureRandom()
    }
}
