package dev.housepoints.nearby

import dev.housepoints.sync.Transport
import dev.housepoints.sync.TransportClosedException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** Delivers one chunk to the peer; returns once it is on its way, throws IOException if it cannot be. */
public fun interface ChunkSink {
    public suspend fun deliver(chunk: ByteArray)
}

/**
 * A [Transport] that chunks frames into a [ChunkSink] and reassembles chunks fed to [onChunk].
 * The Nearby link feeds it payloads; tests feed it from another FramedTransport.
 *
 * Anything the [FrameAssembler] rejects closes the incoming side, so the sync session sees the link as
 * gone and closes it, which runs [onClose].
 */
public class FramedTransport(private val sink: ChunkSink, private val onClose: suspend () -> Unit) : Transport {
    private val chunker = FrameChunker()
    private val assembler = FrameAssembler()
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val closed = AtomicBoolean(false)
    private val sending = Mutex()

    override suspend fun send(frame: ByteArray) {
        sending.withLock {
            chunker.chunk(frame).forEach { chunk ->
                if (closed.get()) throw TransportClosedException("transport closed")
                sink.deliver(chunk)
            }
        }
    }

    override suspend fun receive(): ByteArray? = incoming.receiveCatching().getOrNull()

    override suspend fun close() {
        if (closed.compareAndSet(false, true)) {
            incoming.close()
            onClose()
        }
    }

    /** A chunk arrived from the peer. Safe to call from any thread. */
    public fun onChunk(chunk: ByteArray) {
        val result = synchronized(assembler) { assembler.accept(chunk) }
        when (result) {
            is Assembly.Frames -> result.frames.forEach { incoming.trySend(it) }
            is Assembly.Invalid -> onRemoteClosed()
        }
    }

    /** The peer or the link went away. Frames already reassembled are still delivered. */
    public fun onRemoteClosed() {
        closed.set(true)
        incoming.close()
    }
}
