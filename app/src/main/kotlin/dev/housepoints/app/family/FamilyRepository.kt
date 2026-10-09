package dev.housepoints.app.family

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.Payload
import dev.housepoints.ledger.DecodedOp
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Periods
import dev.housepoints.ledger.Projection
import dev.housepoints.sync.OpFactory
import dev.housepoints.sync.OpLog
import dev.housepoints.sync.VersionVector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

fun interface Clock {
    fun now(): InstantMs

    companion object {
        val SYSTEM: Clock = Clock { InstantMs(System.currentTimeMillis()) }
    }
}

sealed interface RecordResult {
    data class Recorded(val ops: List<Op>) : RecordResult
    data object NoFamily : RecordResult
}

/** What the app knows at a moment: still loading, no family on this phone yet, or a family's state. */
sealed interface FamilySnapshot {
    data object Loading : FamilySnapshot
    data object NoFamily : FamilySnapshot
    data class Ready(val familyId: FamilyId, val state: FamilyState) : FamilySnapshot
}

/**
 * The app's single owner of family state (SAD §3.6). It keeps every op decoded once (ops never change),
 * recalculates on each change to the log and at each time boundary that changes what is shown (local
 * midnight and payday), and records new ops through [OpFactory].
 */
class FamilyRepository(
    private val log: OpLog,
    val self: DeviceId,
    private val clock: Clock,
    private val compute: CoroutineDispatcher,
) {
    private val cacheLock = Mutex()
    private val refreshLock = Mutex()
    private val decoded = ArrayList<DecodedOp>()
    private var cachedVector = VersionVector.EMPTY
    private var factory: OpFactory? = null

    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private val _snapshot = MutableStateFlow<FamilySnapshot>(FamilySnapshot.Loading)
    val snapshot: StateFlow<FamilySnapshot> = _snapshot.asStateFlow()

    /** Starts watching the log; call once from the application scope. */
    fun start(scope: CoroutineScope) {
        scope.launch { log.changes.collect { refreshes.trySend(Unit) } }
        scope.launch {
            refreshes.trySend(Unit)
            while (true) {
                val wakeAt = refresh()
                val waitMs = (wakeAt.value - clock.now().value).coerceAtLeast(MIN_WAIT_MS)
                withTimeoutOrNull(waitMs) { refreshes.receive() }
            }
        }
    }

    /** Recalculates immediately and returns the result (used around a sync to compare before and after). */
    suspend fun refreshNow(): FamilySnapshot {
        refresh()
        return _snapshot.value
    }

    /** Asks for a recalculation now (for example after the clock or timezone changed). */
    fun invalidate() {
        refreshes.trySend(Unit)
    }

    /** Records payloads as new ops from this phone. */
    suspend fun record(payloads: List<Payload>): RecordResult {
        val opFactory = factoryOrNull() ?: return RecordResult.NoFamily
        val ops = opFactory.record(payloads)
        refreshes.trySend(Unit)
        return RecordResult.Recorded(ops)
    }

    /** Starts a new family on this phone: the first op names the family (SPEC FR-1). */
    suspend fun createFamily(familyId: FamilyId, payloads: List<Payload>): List<Op> {
        val opFactory = cacheLock.withLock {
            OpFactory(familyId, self, log, clock::now).also { factory = it }
        }
        val ops = opFactory.record(payloads)
        refreshes.trySend(Unit)
        return ops
    }

    private suspend fun factoryOrNull(): OpFactory? = cacheLock.withLock {
        factory ?: decoded.firstOrNull()?.op?.familyId?.let { family -> OpFactory(family, self, log, clock::now).also { factory = it } }
    }

    /** Recalculates and returns when the next recalculation is due even if nothing changes. */
    private suspend fun refresh(): InstantMs = refreshLock.withLock { recalculate() }

    private suspend fun recalculate(): InstantMs = withContext(compute) {
        val ops = cacheLock.withLock {
            val fresh = log.opsAfter(cachedVector)
            fresh.forEach { op ->
                decoded += DecodedOp.of(op)
                cachedVector = cachedVector.withOp(op)
            }
            decoded.toList()
        }
        val now = clock.now()
        if (ops.isEmpty()) {
            _snapshot.value = FamilySnapshot.NoFamily
            return@withContext InstantMs(now.value + IDLE_WAKE_MS)
        }
        val state = Projection.projectDecoded(ops, now)
        _snapshot.value = FamilySnapshot.Ready(ops.first().op.familyId, state)
        nextBoundary(state, now)
    }

    private fun nextBoundary(state: FamilyState, now: InstantMs): InstantMs {
        val family = state.family ?: return InstantMs(now.value + IDLE_WAKE_MS)
        val periods = Periods(family.zone, family.weekStart)
        val payday = periods.containing(now).end
        val tomorrow = periods.localDate(now).plusDays(1).atStartOfDay(family.zone).toInstant()
        return InstantMs(minOf(payday.value, Instant.from(tomorrow).toEpochMilli()))
    }

    private companion object {
        const val MIN_WAIT_MS = 250L
        const val IDLE_WAKE_MS = 60 * 60 * 1000L
    }
}
