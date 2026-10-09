package dev.housepoints.nearby

import dev.housepoints.sync.Transport

/** Delivers one chunk to the peer; returns once it has been delivered, throws IOException if it cannot be. */
public fun interface ChunkSink {
    public suspend fun deliver(chunk: ByteArray)
}

/**
 * A [Transport] that chunks frames into a [ChunkSink] and reassembles chunks fed to [onChunk].
 * The Nearby link feeds it payloads; tests feed it from another FramedTransport.
 */
public class FramedTransport(private val sink: ChunkSink, private val onClose: suspend () -> Unit) : Transport {
    override suspend fun send(frame: ByteArray): Unit = TODO("red")
    override suspend fun receive(): ByteArray? = TODO("red")
    override suspend fun close(): Unit = TODO("red")

    /** A chunk arrived from the peer. */
    public fun onChunk(chunk: ByteArray): Unit = TODO("red")

    /** The peer or the link went away. */
    public fun onRemoteClosed(): Unit = TODO("red")
}
