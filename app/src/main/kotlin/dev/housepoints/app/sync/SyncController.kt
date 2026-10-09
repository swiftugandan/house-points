package dev.housepoints.app.sync

import android.content.Context
import dev.housepoints.app.family.Clock
import dev.housepoints.app.family.FamilyRepository
import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.Points
import dev.housepoints.data.SyncHistoryEntry
import dev.housepoints.data.SyncHistoryStore
import dev.housepoints.ledger.FamilyState
import dev.housepoints.nearby.NearbyException
import dev.housepoints.nearby.NearbyLink
import dev.housepoints.nearby.Peer
import dev.housepoints.sync.FamilyKey
import dev.housepoints.sync.OpLog
import dev.housepoints.sync.SyncOutcome
import dev.housepoints.sync.SyncSession
import dev.housepoints.sync.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

sealed interface SyncState {
    data object Idle : SyncState
    data class Searching(val peers: List<Peer>) : SyncState
    data class Syncing(val peerName: String) : SyncState
    data class Finished(val peerName: String, val outcome: SyncOutcome, val newEntries: Int, val recalculated: List<Recalculation>) : SyncState
    data class Failed(val message: String) : SyncState
}

/** SPEC FR-37: a balance that moved for reasons other than the entries that just arrived. */
data class Recalculation(val child: ChildId, val change: Points)

/**
 * Runs one sync at a time over Nearby (SAD §3.5, §4.3). While the Sync screen is open both phones advertise
 * and discover; when the other phone appears they connect automatically and run a [SyncSession].
 */
class SyncController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val log: OpLog,
    private val repository: FamilyRepository,
    private val history: SyncHistoryStore,
    private val self: DeviceId,
    private val clock: Clock,
    private val onHistoryChanged: suspend () -> Unit,
) {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val sessionLock = Mutex()
    private var link: NearbyLink? = null
    private var jobs: List<Job> = emptyList()

    fun start(family: FamilyId, key: FamilyKey, phoneName: String) {
        stop()
        val nearby = NearbyLink(context, family, self, phoneName)
        link = nearby
        _state.value = SyncState.Searching(emptyList())
        jobs = listOf(
            scope.launch {
                try {
                    nearby.peers().collect { peers ->
                        if (_state.value is SyncState.Searching) _state.value = SyncState.Searching(peers)
                        peers.firstOrNull()?.let { peer -> if (_state.value is SyncState.Searching) connect(nearby, peer, family, key) }
                    }
                } catch (e: NearbyException) {
                    _state.value = SyncState.Failed(explain(e))
                }
            },
            scope.launch {
                while (true) {
                    val transport = nearby.awaitIncoming()
                    run(transport, "the other phone", family, key)
                }
            },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        link?.stop()
        link = null
        if (_state.value !is SyncState.Finished) _state.value = SyncState.Idle
    }

    fun reset() {
        _state.value = SyncState.Idle
    }

    private fun connect(nearby: NearbyLink, peer: Peer, family: FamilyId, key: FamilyKey) {
        _state.value = SyncState.Syncing(peer.name)
        scope.launch {
            try {
                run(nearby.connect(peer), peer.name, family, key)
            } catch (e: IOException) {
                _state.value = SyncState.Failed(explain(e))
            }
        }
    }

    private suspend fun run(transport: Transport, peerName: String, family: FamilyId, key: FamilyKey) = sessionLock.withLock {
        _state.value = SyncState.Syncing(peerName)
        val before = (repository.refreshNow() as? FamilySnapshot.Ready)?.state
        val beforeVector = log.vector()
        val started = clock.now()
        val outcome = SyncSession(family, key, self, log, transport, clock::now).run()
        history.add(SyncHistoryEntry.of(outcome, (outcome as? SyncOutcome.Completed)?.peer, started, clock.now()))
        onHistoryChanged()
        val after = (repository.refreshNow() as? FamilySnapshot.Ready)?.state
        val arrived = log.opsAfter(beforeVector).mapNotNull { OpCodec.decodePayload(it) as? EntryRecorded }
        _state.value = SyncState.Finished(peerName, outcome, arrived.size, recalculations(before, after, arrived))
        link?.stop()
        jobs.forEach { it.cancel() }
    }

    /** Displayed change per child minus the effect of the entries that just arrived (SPEC FR-37). */
    private fun recalculations(before: FamilyState?, after: FamilyState?, arrived: List<EntryRecorded>): List<Recalculation> {
        if (after == null) return emptyList()
        val arrivedIds = arrived.map { it.entryId }.toSet()
        return after.accounts.mapNotNull { (child, account) ->
            val previous = before?.account(child)?.displayed ?: return@mapNotNull null
            val fromNewEntries = account.lines.filter { it.entry.entryId in arrivedIds && it.entry.effectiveAt < after.asOf }
                .fold(Points.ZERO) { sum, line -> sum + line.effect }
            val change = account.displayed - previous - fromNewEntries
            if (change == Points.ZERO) null else Recalculation(child, change)
        }
    }

    private fun explain(e: IOException): String = when {
        e is NearbyException && e.statusCode != null ->
            "This phone couldn't use Bluetooth or Wi-Fi to look for the other phone. Check that Bluetooth, Wi-Fi and Location are on, then try again."
        else -> "The phones lost touch before they finished. Anything that arrived is kept; try again to send the rest."
    }
}
