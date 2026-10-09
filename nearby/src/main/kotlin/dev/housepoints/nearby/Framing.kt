package dev.housepoints.nearby

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
}

/** Splits sync frames into chunks that fit one Nearby `BYTES` payload. */
public class FrameChunker {
    public fun chunk(frame: ByteArray): List<ByteArray> = TODO("red")
}

/** What [FrameAssembler.accept] produced. */
public sealed interface Assembly {
    /** Frames now complete, in the order they were sent (possibly none yet). */
    public data class Frames(val frames: List<ByteArray>) : Assembly

    /** The peer sent something no honest peer sends; the link should be dropped. */
    public data class Invalid(val reason: String) : Assembly
}

/** Reassembles chunks into frames with bounded memory, releasing frames strictly in send order. */
public class FrameAssembler {
    public fun accept(chunk: ByteArray): Assembly = TODO("red")
}
