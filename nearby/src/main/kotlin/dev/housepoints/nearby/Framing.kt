package dev.housepoints.nearby

import java.nio.ByteBuffer

/**
 * Chunk layout: `frameId u32 ‖ index u16 ‖ count u16 ‖ data`. A chunk is at most [MAX_CHUNK_BYTES],
 * the Nearby `BYTES` payload limit; a frame is at most [MAX_FRAME_BYTES].
 */
public object Framing {
    public const val MAX_CHUNK_BYTES: Int = 32 * 1024
    public const val HEADER_BYTES: Int = 8
    public const val MAX_CHUNK_DATA: Int = MAX_CHUNK_BYTES - HEADER_BYTES
    public const val MAX_FRAME_BYTES: Int = 1024 * 1024
    public const val MAX_PENDING_FRAMES: Int = 4
    internal const val MAX_CHUNKS: Int = (MAX_FRAME_BYTES + MAX_CHUNK_DATA - 1) / MAX_CHUNK_DATA
    internal const val U16_MASK: Int = 0xFFFF
}

/** Splits sync frames into chunks that fit one Nearby `BYTES` payload. Frame ids count up from 0. */
public class FrameChunker {
    private var nextFrameId = 0

    /** [frame] must be at most [Framing.MAX_FRAME_BYTES]; sync frames are at most half that. */
    public fun chunk(frame: ByteArray): List<ByteArray> {
        require(frame.size <= Framing.MAX_FRAME_BYTES) { "frame of ${frame.size} bytes exceeds ${Framing.MAX_FRAME_BYTES}" }
        val id = nextFrameId++
        val count = maxOf(1, (frame.size + Framing.MAX_CHUNK_DATA - 1) / Framing.MAX_CHUNK_DATA)
        return (0 until count).map { index ->
            val start = index * Framing.MAX_CHUNK_DATA
            val end = minOf(frame.size, start + Framing.MAX_CHUNK_DATA)
            ByteBuffer.allocate(Framing.HEADER_BYTES + end - start)
                .putInt(id).putShort(index.toShort()).putShort(count.toShort())
                .put(frame, start, end - start)
                .array()
        }
    }
}

/** What [FrameAssembler.accept] produced. */
public sealed interface Assembly {
    /** Frames now complete, in the order they were sent (possibly none yet). */
    public data class Frames(val frames: List<ByteArray>) : Assembly

    /** The peer sent something no honest peer sends; the link should be dropped. */
    public data class Invalid(val reason: String) : Assembly
}

/**
 * Reassembles chunks into frames, releasing frames strictly in send order. Memory is bounded: at most
 * [Framing.MAX_PENDING_FRAMES] frames may be incomplete or waiting for an earlier one.
 */
public class FrameAssembler {
    private class Partial(val count: Int) {
        val chunks = arrayOfNulls<ByteArray>(count)
        var received = 0
    }

    private var nextToRelease = 0
    private val partials = HashMap<Int, Partial>()
    private val completed = HashMap<Int, ByteArray>()

    public fun accept(chunk: ByteArray): Assembly {
        if (chunk.size < Framing.HEADER_BYTES) return Assembly.Invalid("chunk shorter than its header")
        val header = ByteBuffer.wrap(chunk)
        val id = header.int
        val index = header.short.toInt() and Framing.U16_MASK
        val count = header.short.toInt() and Framing.U16_MASK
        val data = chunk.copyOfRange(Framing.HEADER_BYTES, chunk.size)
        problemWith(id, index, count, data)?.let { return Assembly.Invalid(it) }

        val partial = partials[id] ?: Partial(count).also { partials[id] = it }
        if (partial.count != count) return Assembly.Invalid("chunk count changed within frame $id")
        if (partial.chunks[index] != null) return Assembly.Invalid("chunk $index of frame $id repeated")
        partial.chunks[index] = data
        partial.received++
        if (partial.received == count) {
            partials.remove(id)
            completed[id] = join(partial)
        }
        return Assembly.Frames(release())
    }

    private fun problemWith(id: Int, index: Int, count: Int, data: ByteArray): String? = when {
        count == 0 || count > Framing.MAX_CHUNKS -> "frame $id claims $count chunks"
        index >= count -> "chunk index $index beyond count $count"
        data.size > Framing.MAX_CHUNK_DATA -> "chunk larger than ${Framing.MAX_CHUNK_BYTES} bytes"
        id - nextToRelease < 0 || id in completed -> "frame $id already delivered"
        id !in partials && partials.size + completed.size >= Framing.MAX_PENDING_FRAMES -> "too many frames in flight"
        else -> null
    }

    private fun join(partial: Partial): ByteArray {
        val size = partial.chunks.sumOf { it?.size ?: 0 }
        val out = ByteBuffer.allocate(size)
        partial.chunks.forEach { it?.let(out::put) }
        return out.array()
    }

    private fun release(): List<ByteArray> {
        val ready = ArrayList<ByteArray>()
        while (true) {
            val frame = completed.remove(nextToRelease) ?: return ready
            ready += frame
            nextToRelease++
        }
    }
}
