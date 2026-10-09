package dev.housepoints.sync

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import java.io.IOException

/**
 * A reliable, ordered, bidirectional channel of frames between two devices (SAD §4.4).
 *
 * [send] throws [IOException] once the channel is closed, and returns only when the frame will be
 * delivered unless the link fails. [receive] returns null once the channel is closed and every frame
 * sent before closing has been delivered.
 */
public interface Transport {
    public suspend fun send(frame: ByteArray)
    public suspend fun receive(): ByteArray?
    public suspend fun close()
}

public class TransportClosedException(message: String) : IOException(message)

/** Two connected in-process transports: for tests and the e2e-mock suites. */
public object LoopbackTransport {
    public fun pair(): Pair<Transport, Transport> {
        val forward = Channel<ByteArray>(Channel.UNLIMITED)
        val backward = Channel<ByteArray>(Channel.UNLIMITED)
        return ChannelTransport(outgoing = forward, incoming = backward) to
            ChannelTransport(outgoing = backward, incoming = forward)
    }

    private class ChannelTransport(
        private val outgoing: Channel<ByteArray>,
        private val incoming: Channel<ByteArray>,
    ) : Transport {
        override suspend fun send(frame: ByteArray) {
            try {
                outgoing.send(frame)
            } catch (e: ClosedSendChannelException) {
                throw TransportClosedException(e.message ?: "loopback closed")
            }
        }

        override suspend fun receive(): ByteArray? = incoming.receiveCatching().getOrNull()

        /** Closing either end closes both directions; frames already sent are still delivered. */
        override suspend fun close() {
            outgoing.close()
            incoming.close()
        }
    }
}
