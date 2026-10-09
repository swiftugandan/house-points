package dev.housepoints.sync

import java.io.IOException

/**
 * A reliable, ordered, bidirectional channel of frames between two devices (SAD §4.4).
 * [send] throws [IOException] once the channel is closed; [receive] returns null once it is closed and
 * every frame sent before closing has been delivered.
 */
public interface Transport {
    public suspend fun send(frame: ByteArray)
    public suspend fun receive(): ByteArray?
    public suspend fun close()
}

public class TransportClosedException(message: String) : IOException(message)

/** Two connected in-process transports: for tests and the e2e-mock suites. */
public object LoopbackTransport {
    public fun pair(): Pair<Transport, Transport> = TODO("red")
}
