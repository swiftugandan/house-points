package dev.housepoints.sync

import dev.housepoints.contracts.Seq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionVectorTest {
    private val a = TestOps.device(1)

    @Test
    fun `absent devices read as no ops`() {
        assertEquals(Seq.NONE, VersionVector.EMPTY[a])
        assertEquals(VersionVector.EMPTY, VersionVector(mapOf(a to Seq.NONE)))
    }

    @Test
    fun `covers is true for ops at or below the device's sequence`() {
        val vector = VersionVector(mapOf(a to Seq(2)))
        assertTrue(vector.covers(TestOps.op(a, 2)))
        assertFalse(vector.covers(TestOps.op(a, 3)))
        assertFalse(vector.covers(TestOps.op(TestOps.device(2), 1)))
    }

    @Test
    fun `withOp advances only forward`() {
        val vector = VersionVector(mapOf(a to Seq(2)))
        assertEquals(Seq(3), vector.withOp(TestOps.op(a, 3))[a])
        assertEquals(Seq(2), vector.withOp(TestOps.op(a, 1))[a])
    }
}
