package dev.housepoints.nearby

import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

/** Two [FramedTransport]s wired back to back, standing in for two phones joined by Nearby. */
object FramedPair {
    private val counters = IdentityHashMap<FramedTransport, AtomicInteger>()

    fun create(): Pair<FramedTransport, FramedTransport> {
        val leftRef = AtomicReference<FramedTransport>()
        val rightRef = AtomicReference<FramedTransport>()
        val leftCount = AtomicInteger()
        val rightCount = AtomicInteger()
        val left = FramedTransport(
            sink = { chunk -> leftCount.incrementAndGet(); rightRef.get().onChunk(chunk) },
            onClose = { rightRef.get().onRemoteClosed() },
        )
        val right = FramedTransport(
            sink = { chunk -> rightCount.incrementAndGet(); leftRef.get().onChunk(chunk) },
            onClose = { leftRef.get().onRemoteClosed() },
        )
        leftRef.set(left)
        rightRef.set(right)
        synchronized(counters) {
            counters[left] = leftCount
            counters[right] = rightCount
        }
        return left to right
    }

    fun chunksSent(transport: FramedTransport): Int = synchronized(counters) { counters.getValue(transport).get() }
}
