package dev.housepoints.nearby

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChildUpsert
import dev.housepoints.sync.FamilyKey
import dev.housepoints.sync.SimDevice
import dev.housepoints.sync.SyncOutcome
import dev.housepoints.sync.TestOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * e2e with mocked peers: two simulated phones run real sync sessions through [FramedTransport]s wired
 * back to back, the same chunking path Nearby payloads take, with OPS frames well over 100 KiB.
 */
class FramedSyncE2eTest {
    private val longName = "n".repeat(1_000)

    @Test
    fun `phones converge through chunked frames larger than one Nearby payload`() = runBlocking(Dispatchers.Default) {
        val key = FamilyKey.generate()
        val a = SimDevice(TestOps.device(1), key)
        val b = SimDevice(TestOps.device(2), key)
        repeat(600) { a.factory.record(listOf(ChildUpsert(ChildId(UUID.randomUUID()), name = longName))) }
        repeat(50) { b.factory.record(listOf(ChildUpsert(ChildId(UUID.randomUUID()), name = longName))) }

        val pair = FramedPair.create()
        val (onA, onB) = SimDevice.sync(a, b, pair)

        assertTrue("$onA", onA is SyncOutcome.Completed)
        assertTrue("$onB", onB is SyncOutcome.Completed)
        assertEquals(a.log.all().toSet(), b.log.all().toSet())
        assertTrue("frames should span many chunks", FramedPair.chunksSent(pair.first) > 600 * 1_000 / Framing.MAX_CHUNK_DATA)
    }

    @Test
    fun `a wrong key through the framed path is still refused`() = runBlocking(Dispatchers.Default) {
        val a = SimDevice(TestOps.device(1), FamilyKey.generate())
        val b = SimDevice(TestOps.device(2), FamilyKey.generate())
        val (onA, _) = SimDevice.sync(a, b, FramedPair.create(), timeoutMs = 2_000)
        assertTrue("$onA", onA is SyncOutcome.Rejected)
    }
}
