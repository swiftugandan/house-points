package dev.housepoints.sync

import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.Seq
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour every [OpLog] must have (SAD §4.4). Implementations subclass this and supply [newLog];
 * :sync runs it against [InMemoryOpLog] and :data against SQLite on a device.
 */
public abstract class OpLogContract {
    protected abstract fun newLog(): OpLog

    private val a = TestOps.device(1)
    private val b = TestOps.device(2)

    @Test
    public fun an_empty_log_has_an_empty_vector_and_zero_lamport(): Unit = runBlocking {
        val log = newLog()
        assertEquals(VersionVector.EMPTY, log.vector())
        assertEquals(Lamport.ZERO, log.maxLamport())
        assertEquals(emptyList<Op>(), log.all())
    }

    @Test
    public fun contiguous_ops_are_stored_and_reflected_in_the_vector(): Unit = runBlocking {
        val log = newLog()
        val result = log.append(TestOps.ops(a, 1L..3L) + TestOps.ops(b, 1L..2L))
        assertEquals(AppendResult(added = 5, duplicates = 0), result)
        assertEquals(VersionVector(mapOf(a to Seq(3), b to Seq(2))), log.vector())
        assertEquals(Lamport(3), log.maxLamport())
    }

    @Test
    public fun a_batch_out_of_order_is_sorted_before_storing(): Unit = runBlocking {
        val log = newLog()
        val result = log.append(TestOps.ops(a, 1L..4L).reversed())
        assertEquals(4, result.added)
        assertEquals(Seq(4), log.vector()[a])
    }

    @Test
    public fun duplicates_by_op_id_or_device_and_sequence_are_ignored(): Unit = runBlocking {
        val log = newLog()
        val first = TestOps.op(a, 1)
        log.append(listOf(first))
        val sameSeqOtherId = TestOps.op(a, 1, lamport = 9)
        val result = log.append(listOf(first, sameSeqOtherId, TestOps.op(a, 2)))
        assertEquals(AppendResult(added = 1, duplicates = 2), result)
        assertEquals(listOf(first.opId, TestOps.op(a, 2).opId).size, log.all().size)
    }

    @Test
    public fun an_op_that_would_leave_a_gap_is_skipped(): Unit = runBlocking {
        val log = newLog()
        val result = log.append(listOf(TestOps.op(a, 1), TestOps.op(a, 3)))
        assertEquals(AppendResult(added = 1, duplicates = 0), result)
        assertEquals(Seq(1), log.vector()[a])
        assertEquals(1, log.append(listOf(TestOps.op(a, 2))).added)
        assertEquals(1, log.append(listOf(TestOps.op(a, 3))).added)
        assertEquals(Seq(3), log.vector()[a])
    }

    @Test
    public fun ops_after_a_vector_are_exactly_the_missing_ones_in_origin_order(): Unit = runBlocking {
        val log = newLog()
        log.append(TestOps.ops(b, 1L..3L) + TestOps.ops(a, 1L..3L))
        val missing = log.opsAfter(VersionVector(mapOf(a to Seq(1), b to Seq(3))))
        assertEquals(listOf(Seq(2), Seq(3)), missing.map { it.originSeq })
        assertTrue(missing.all { it.originDevice == a })
        val everything = log.opsAfter(VersionVector.EMPTY)
        assertEquals(everything.sortedWith(Op.ORIGIN_ORDER), everything)
        assertEquals(6, everything.size)
    }

    @Test
    public fun stored_ops_come_back_byte_identical(): Unit = runBlocking {
        val log = newLog()
        val ops = TestOps.ops(a, 1L..2L)
        log.append(ops)
        assertEquals(ops.toSet(), log.all().toSet())
    }

    @Test
    public fun changes_emits_after_an_append_that_stored_something(): Unit = runBlocking {
        val log = newLog()
        val emitted = async { withTimeout(5_000) { log.changes.first() } }
        yield()
        log.append(listOf(TestOps.op(a, 1)))
        emitted.await()
    }
}
