package dev.housepoints.sync

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicLong

/** A simulated phone: its own log, op factory and identity, sharing one family key. */
public class SimDevice(
    public val id: DeviceId,
    public val key: FamilyKey,
    public val family: FamilyId = TestOps.FAMILY,
    public val log: OpLog = InMemoryOpLog(),
) {
    private val ticks = AtomicLong(1_760_000_000_000)
    public val clock: () -> InstantMs = { InstantMs(ticks.incrementAndGet()) }
    public val factory: OpFactory = OpFactory(family, id, log, clock)

    public fun session(transport: Transport, timeoutMs: Long = SyncSession.DEFAULT_RECEIVE_TIMEOUT_MS): SyncSession =
        SyncSession(family, key, id, log, transport, clock, timeoutMs)

    public companion object {
        /** Runs one session between [a] and [b] over [transports] (default: a fresh loopback pair). */
        public suspend fun sync(
            a: SimDevice,
            b: SimDevice,
            transports: Pair<Transport, Transport> = LoopbackTransport.pair(),
            timeoutMs: Long = SyncSession.DEFAULT_RECEIVE_TIMEOUT_MS,
        ): Pair<SyncOutcome, SyncOutcome> = coroutineScope {
            val first = async { a.session(transports.first, timeoutMs).run() }
            val second = async { b.session(transports.second, timeoutMs).run() }
            first.await() to second.await()
        }
    }
}
