package dev.housepoints.nearby

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.random.Random

class FramingTest {
    private fun frame(size: Int) = Random(size).nextBytes(size)

    private fun roundTrip(frames: List<ByteArray>): List<ByteArray> {
        val chunker = FrameChunker()
        val assembler = FrameAssembler()
        return frames.flatMap { chunker.chunk(it) }.flatMap { chunk ->
            (assembler.accept(chunk) as Assembly.Frames).frames
        }
    }

    @Test
    fun `frames of every awkward size survive chunking`() {
        val sizes = listOf(0, 1, Framing.MAX_CHUNK_DATA - 1, Framing.MAX_CHUNK_DATA, Framing.MAX_CHUNK_DATA + 1, 100 * 1024, Framing.MAX_FRAME_BYTES)
        val frames = sizes.map(::frame)
        val out = roundTrip(frames)
        assertEquals(frames.size, out.size)
        frames.zip(out).forEach { (sent, got) -> assertArrayEquals(sent, got) }
    }

    @Test
    fun `every chunk fits a Nearby bytes payload`() {
        val chunks = FrameChunker().chunk(frame(Framing.MAX_FRAME_BYTES))
        assertTrue(chunks.all { it.size <= Framing.MAX_CHUNK_BYTES })
        assertEquals(Framing.MAX_CHUNK_BYTES, 32 * 1024)
    }

    @Test
    fun `a frame finished early is held until the frames before it are complete`() {
        val chunker = FrameChunker()
        val first = chunker.chunk(frame(70_000))
        val second = chunker.chunk(frame(10))
        val assembler = FrameAssembler()
        assertEquals(emptyList<ByteArray>(), (assembler.accept(second.single()) as Assembly.Frames).frames)
        val released = first.flatMap { (assembler.accept(it) as Assembly.Frames).frames }
        assertEquals(listOf(70_000, 10), released.map { it.size })
    }

    @Test
    fun `oversized frames are refused when chunking and when assembling`() {
        val tooManyChunks = Framing.MAX_FRAME_BYTES / Framing.MAX_CHUNK_DATA + 2
        val header = ByteBuffer.allocate(Framing.HEADER_BYTES).putInt(0).putShort(0).putShort(tooManyChunks.toShort()).array()
        assertTrue(FrameAssembler().accept(header) is Assembly.Invalid)
        assertTrue(runCatching { FrameChunker().chunk(ByteArray(Framing.MAX_FRAME_BYTES + 1)) }.isFailure)
    }

    @Test
    fun `malformed chunks are invalid, never exceptions`() {
        val assembler = FrameAssembler()
        assertTrue(assembler.accept(byteArrayOf(1, 2)) is Assembly.Invalid)
        val badIndex = ByteBuffer.allocate(Framing.HEADER_BYTES).putInt(0).putShort(3).putShort(2).array()
        assertTrue(FrameAssembler().accept(badIndex) is Assembly.Invalid)
    }

    @Test
    fun `a peer cannot make the assembler hold unbounded partial frames`() {
        val assembler = FrameAssembler()
        val results = (1..Framing.MAX_PENDING_FRAMES + 1).map { id ->
            val header = ByteBuffer.allocate(Framing.HEADER_BYTES).putInt(id).putShort(0).putShort(2).array()
            assembler.accept(header)
        }
        assertTrue(results.last() is Assembly.Invalid)
    }
}
