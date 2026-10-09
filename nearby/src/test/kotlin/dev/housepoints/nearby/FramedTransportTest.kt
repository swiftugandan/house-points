package dev.housepoints.nearby

import dev.housepoints.sync.TransportClosedException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FramedTransportTest {
    @Test
    fun `a large frame crosses as chunks and arrives whole`() = runTest {
        val (a, b) = FramedPair.create()
        val frame = Random(1).nextBytes(150 * 1024)
        a.send(frame)
        assertArrayEquals(frame, b.receive())
        assertTrue(FramedPair.chunksSent(a) > 1)
    }

    @Test
    fun `closing one end ends the other's receive and fails later sends`() = runTest {
        val (a, b) = FramedPair.create()
        a.send(byteArrayOf(1))
        a.close()
        assertArrayEquals(byteArrayOf(1), b.receive())
        assertNull(b.receive())
        val failed = runCatching { a.send(byteArrayOf(2)) }.exceptionOrNull()
        assertTrue("$failed", failed is TransportClosedException)
    }

    @Test
    fun `garbage from the peer closes the transport`() = runTest {
        val (_, b) = FramedPair.create()
        b.onChunk(byteArrayOf(9))
        assertNull(b.receive())
        assertEquals(0, 0)
    }
}
