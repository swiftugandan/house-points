package dev.housepoints.sync

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChildUpsert
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.Op
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class SyncSessionTest {
    private val key = FamilyKey.generate()
    private fun device(n: Int, family: FamilyId = TestOps.FAMILY, familyKey: FamilyKey = key) =
        SimDevice(TestOps.device(n), familyKey, family)

    private suspend fun SimDevice.create(count: Int) {
        repeat(count) { factory.record(listOf(ChildUpsert(ChildId(UUID.randomUUID()), name = "c$it"))) }
    }

    private fun runSync(block: suspend () -> Unit) = runBlocking(Dispatchers.Default) { block() }

    @Test
    fun `two devices exchange exactly what the other lacks`() = runSync {
        val a = device(1).apply { create(5) }
        val b = device(2).apply { create(3) }
        val (outA, outB) = SimDevice.sync(a, b)
        assertTrue("$outA", outA is SyncOutcome.Completed)
        assertEquals(5, (outA as SyncOutcome.Completed).sent)
        assertEquals(3, outA.received)
        assertEquals(b.id, outA.peer)
        assertEquals(3, (outB as SyncOutcome.Completed).sent)
        assertEquals(a.log.all().toSet(), b.log.all().toSet())
        assertEquals(a.log.vector(), b.log.vector())
    }

    @Test
    fun `a second session with nothing new sends nothing`() = runSync {
        val a = device(1).apply { create(4) }
        val b = device(2)
        SimDevice.sync(a, b)
        val (outA, outB) = SimDevice.sync(a, b)
        assertEquals(0, (outA as SyncOutcome.Completed).sent + outA.received)
        assertEquals(0, (outB as SyncOutcome.Completed).sent)
    }

    @Test
    fun `large logs travel in several batches`() = runSync {
        val a = device(1).apply { create(700) }
        val b = device(2)
        SimDevice.sync(a, b)
        assertEquals(700, b.log.all().size)
    }

    @Test
    fun `a wrong family key is rejected before any op is exchanged`() = runSync {
        val a = device(1).apply { create(2) }
        val b = device(2, familyKey = FamilyKey.generate()).apply { create(2) }
        val (outA, outB) = SimDevice.sync(a, b, timeoutMs = 2_000)
        assertEquals(RejectReason.AUTHENTICATION_FAILED, (outA as SyncOutcome.Rejected).reason)
        assertEquals(RejectReason.AUTHENTICATION_FAILED, (outB as SyncOutcome.Rejected).reason)
        assertEquals(2, a.log.all().size)
        assertEquals(2, b.log.all().size)
    }

    @Test
    fun `devices from different families refuse each other`() = runSync {
        val a = device(1)
        val b = device(2, family = FamilyId(UUID.randomUUID()))
        val (outA, outB) = SimDevice.sync(a, b, timeoutMs = 2_000)
        assertEquals(RejectReason.WRONG_FAMILY, (outA as SyncOutcome.Rejected).reason)
        assertEquals(RejectReason.WRONG_FAMILY, (outB as SyncOutcome.Rejected).reason)
    }

    @Test
    fun `a device will not sync with its own identity`() = runSync {
        val a = device(1)
        val clone = device(1)
        val (outA, _) = SimDevice.sync(a, clone, timeoutMs = 2_000)
        assertEquals(RejectReason.PROTOCOL_ERROR, (outA as SyncOutcome.Rejected).reason)
    }

    @Test
    fun `a tampered encrypted frame aborts the session`() = runSync {
        val a = device(1).apply { create(3) }
        val b = device(2)
        val (ta, tb) = LoopbackTransport.pair()
        // Frames from a: HELLO, AUTH, then the first encrypted frame (VECTOR).
        val tampering = FaultyTransport(ta, corruptFrameNumber = 3)
        val (_, outB) = SimDevice.sync(a, b, tampering to tb, timeoutMs = 2_000)
        assertEquals(RejectReason.TAMPERED, (outB as SyncOutcome.Rejected).reason)
        assertEquals(0, b.log.all().size)
    }

    @Test
    fun `an interrupted session loses nothing and the next one completes without duplicates`() = runSync {
        val a = device(1).apply { create(600) }
        val b = device(2).apply { create(10) }
        val (ta, tb) = LoopbackTransport.pair()
        val dropping = FaultyTransport(ta, closeAfterFrames = 5)
        val (outA, outB) = SimDevice.sync(a, b, dropping to tb, timeoutMs = 2_000)
        assertTrue("$outA", outA is SyncOutcome.Interrupted)
        assertTrue("$outB", outB is SyncOutcome.Interrupted)
        val partial = b.log.all().size
        assertTrue("some batches should have landed: $partial", partial in 11 until 610)

        SimDevice.sync(a, b)
        val all = b.log.all()
        assertEquals(610, all.size)
        assertEquals(610, all.map(Op::opId).toSet().size)
        assertEquals(a.log.all().toSet(), all.toSet())
    }

    /** Wraps a transport to corrupt one outgoing frame or to drop the link after some frames. */
    private class FaultyTransport(
        private val inner: Transport,
        private val corruptFrameNumber: Int = -1,
        private val closeAfterFrames: Int = Int.MAX_VALUE,
    ) : Transport {
        private val sent = AtomicInteger(0)

        override suspend fun send(frame: ByteArray) {
            val number = sent.incrementAndGet()
            if (number > closeAfterFrames) {
                inner.close()
                throw TransportClosedException("link dropped by test")
            }
            val outgoing = if (number == corruptFrameNumber) {
                frame.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x55).toByte() }
            } else {
                frame
            }
            inner.send(outgoing)
        }

        override suspend fun receive(): ByteArray? = inner.receive()
        override suspend fun close() = inner.close()
    }
}
