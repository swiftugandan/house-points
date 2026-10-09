package dev.housepoints.nearby

import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Two [FramedTransport]s wired back to back, standing in for two phones joined by Nearby. */
object FramedPair {
    private val counters = IdentityHashMap<FramedTransport, AtomicInteger>()

    fun create(): Pair<FramedTransport, FramedTransport> {
        lateinit var right: FramedTransport
        val leftCount = AtomicInteger()
        val rightCount = AtomicInteger()
        val left = FramedTransport(
            sink = { chunk -> leftCount.incrementAndGet(); right.onChunk(chunk) },
            onClose = { right.onRemoteClosed() },
        )
        right = FramedTransport(
            sink = { chunk -> rightCount.incrementAndGet(); left.onChunk(chunk) },
            onClose = { left.onRemoteClosed() },
        )
        synchronized(counters) {
            counters[left] = leftCount
            counters[right] = rightCount
        }
        return left to right
    }

    fun chunksSent(transport: FramedTransport): Int = synchronized(counters) { counters.getValue(transport).get() }
}
