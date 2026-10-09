package dev.housepoints.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * e2e against mocked peers: a real [SyncSession] on one end of a loopback link, a scripted peer that
 * misbehaves on the other.
 */
class MockPeerTest {
    private val device = SimDevice(TestOps.device(1), FamilyKey.generate())

    private fun againstScriptedPeer(script: suspend (Transport) -> Unit): SyncOutcome = runBlocking(Dispatchers.Default) {
        val (ours, theirs) = LoopbackTransport.pair()
        val outcome = async { device.session(ours, timeoutMs = 1_000).run() }
        script(theirs)
        outcome.await()
    }

    @Test
    fun `a peer speaking a newer protocol is refused`() {
        val outcome = againstScriptedPeer { peer ->
            val hello = Wire.Hello(protocol = 2, TestOps.FAMILY, TestOps.device(2), ByteArray(Wire.NONCE_BYTES))
            peer.send(Wire.frame(Wire.HELLO, Wire.hello(hello)))
        }
        assertEquals(RejectReason.PROTOCOL_VERSION, (outcome as SyncOutcome.Rejected).reason)
    }

    @Test
    fun `garbage instead of HELLO is a protocol error`() {
        val outcome = againstScriptedPeer { peer -> peer.send(byteArrayOf(9, 9, 9)) }
        assertEquals(RejectReason.PROTOCOL_ERROR, (outcome as SyncOutcome.Rejected).reason)
    }

    @Test
    fun `a peer that skips authentication is refused`() {
        val outcome = againstScriptedPeer { peer ->
            val hello = Wire.Hello(SyncSession.PROTOCOL, TestOps.FAMILY, TestOps.device(2), ByteArray(Wire.NONCE_BYTES))
            peer.send(Wire.frame(Wire.HELLO, Wire.hello(hello)))
            peer.send(Wire.frame(Wire.VECTOR, Wire.vector(VersionVector.EMPTY)))
        }
        assertEquals(RejectReason.PROTOCOL_ERROR, (outcome as SyncOutcome.Rejected).reason)
    }

    @Test
    fun `a silent peer ends the session as interrupted after the timeout`() {
        val outcome = againstScriptedPeer { }
        assertTrue("$outcome", outcome is SyncOutcome.Interrupted)
    }

    @Test
    fun `a forged AUTH is refused without revealing anything`() {
        val outcome = againstScriptedPeer { peer ->
            val hello = Wire.Hello(SyncSession.PROTOCOL, TestOps.FAMILY, TestOps.device(2), ByteArray(Wire.NONCE_BYTES))
            peer.send(Wire.frame(Wire.HELLO, Wire.hello(hello)))
            peer.send(Wire.frame(Wire.AUTH, ByteArray(32)))
        }
        assertEquals(RejectReason.AUTHENTICATION_FAILED, (outcome as SyncOutcome.Rejected).reason)
    }
}
