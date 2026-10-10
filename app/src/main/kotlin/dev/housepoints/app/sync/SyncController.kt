package dev.housepoints.app.sync

import dev.housepoints.app.family.Clock
import dev.housepoints.app.family.FamilyRepository
import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Uuids
import dev.housepoints.data.SyncHistoryEntry
import dev.housepoints.data.SyncHistoryStore
import dev.housepoints.ledger.FamilyState
import dev.housepoints.nearby.NearbyException
import dev.housepoints.sync.FamilyKey
import dev.housepoints.sync.LinkPeer
import dev.housepoints.sync.OpLog
import dev.housepoints.sync.PeerLink
import dev.housepoints.sync.SyncOutcome
import dev.housepoints.sync.SyncSession
import dev.housepoints.sync.Transport
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SyncState {
    data object Idle : SyncState
    data class Searching(val peers: List<LinkPeer>) : SyncState
    data class Syncing(val peerName: String) : SyncState
    data class Finished(val peerName: String, val outcome: SyncOutcome, val newEntries: Int, val recalculated: List<Recalculation>) : SyncState
    data class Failed(val message: String) : SyncState
}

/** SPEC FR-37: a balance that moved for reasons other than the entries that just arrived. */
data class Recalculation(val child: ChildId, val change: Points)

/** How a link is opened for a family; the app supplies the local-network and Bluetooth ones. */
fun interface LinkFactory {
    fun open(family: FamilyId, phoneName: String): PeerLink
}

/**
 * Runs sync sessions over a [PeerLink] (SAD §4.3, ADR-9). One rule avoids two phones dialling each other at
 * once: the phone with the lower device id opens the connection; the other only accepts.
 *
 * - [startAutomatic] runs while the app is in the foreground on the local network: it syncs when the other
 *   phone appears, shortly after anything is recorded, and every minute while both are visible.
 * - [startManual] is the Sync screen: one sync with the first phone found, over whichever link is given.
 */
class SyncController(
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

    /** Interest recalculations from the latest sync (automatic or manual) for the Home screen to show. */
    private val _recalculations = MutableStateFlow<List<Recalculation>>(emptyList())
    val recalculations: StateFlow<List<Recalculation>> = _recalculations.asStateFlow()

    private val sessionLock = Mutex()
    private var link: PeerLink? = null
    private var jobs: List<Job> = emptyList()

    fun startManual(factory: LinkFactory, family: FamilyId, key: FamilyKey, phoneName: String) {
        stop()
        val opened = factory.open(family, phoneName)
        link = opened
        _state.value = SyncState.Searching(emptyList())
        jobs = listOf(
            scope.launch {
                try {
                    opened.peers().collect { peers ->
                        if (_state.value !is SyncState.Searching) return@collect
                        _state.value = SyncState.Searching(peers)
                        val peer = peers.firstOrNull() ?: return@collect
                        if (dials(peer)) dial(opened, peer, family, key, manual = true)
                    }
                } catch (e: IOException) {
                    _state.value = SyncState.Failed(explain(e))
                }
            },
            scope.launch { acceptLoop(opened, family, key, manual = true) },
        )
    }

    @OptIn(FlowPreview::class)
    fun startAutomatic(factory: LinkFactory, family: FamilyId, key: FamilyKey, phoneName: String) {
        stop()
        val opened = factory.open(family, phoneName)
        link = opened
        val visible = MutableStateFlow<List<LinkPeer>>(emptyList())
        val nudges = Channel<Unit>(Channel.CONFLATED)
        jobs = listOf(
            scope.launch {
                try {
                    opened.peers().collect { peers ->
                        if (peers.map { it.device } != visible.value.map { it.device }) nudges.trySend(Unit)
                        visible.value = peers
                    }
                } catch (e: IOException) {
                    // The local network is unavailable; manual sync over Bluetooth still works.
                }
            },
            scope.launch { log.changes.debounce(AFTER_RECORD_MS).collect { nudges.trySend(Unit) } },
            scope.launch {
                while (true) {
                    withTimeoutOrNull(POLL_MS) { nudges.receive() }
                    visible.value.filter(::dials).forEach { peer -> dial(opened, peer, family, key, manual = false) }
                }
            },
            scope.launch { acceptLoop(opened, family, key, manual = false) },
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

    fun dismissRecalculations() {
        _recalculations.value = emptyList()
    }

    private fun dials(peer: LinkPeer): Boolean = Uuids.compare(self.uuid, peer.device.uuid) < 0

    private suspend fun dial(opened: PeerLink, peer: LinkPeer, family: FamilyId, key: FamilyKey, manual: Boolean) {
        try {
            run(opened.connect(peer), peer.name, family, key, manual)
        } catch (e: IOException) {
            if (manual) _state.value = SyncState.Failed(explain(e))
        }
    }

    private suspend fun acceptLoop(opened: PeerLink, family: FamilyId, key: FamilyKey, manual: Boolean) {
        while (true) {
            val transport = opened.awaitIncoming() ?: return
            scope.launch { run(transport, "the other phone", family, key, manual) }
        }
    }

    private suspend fun run(transport: Transport, peerName: String, family: FamilyId, key: FamilyKey, manual: Boolean) = sessionLock.withLock {
        if (manual) _state.value = SyncState.Syncing(peerName)
        val before = (repository.refreshNow() as? FamilySnapshot.Ready)?.state
        val beforeVector = log.vector()
        val started = clock.now()
        val outcome = SyncSession(family, key, self, log, transport, clock::now).run()
        history.add(SyncHistoryEntry.of(outcome, (outcome as? SyncOutcome.Completed)?.peer, started, clock.now()))
        onHistoryChanged()
        val after = (repository.refreshNow() as? FamilySnapshot.Ready)?.state
        val arrived = log.opsAfter(beforeVector).mapNotNull { OpCodec.decodePayload(it) as? EntryRecorded }
        val recalculated = recalculations(before, after, arrived)
        if (recalculated.isNotEmpty()) _recalculations.value = recalculated
        val name = (outcome as? SyncOutcome.Completed)?.peer?.let { peer -> after?.devices?.firstOrNull { it.id == peer }?.name?.ifBlank { null } } ?: peerName
        if (manual) {
            _state.value = SyncState.Finished(name, outcome, arrived.size, recalculated)
            stop()
        }
        // Give the other phone a moment to finish its side before the next automatic pass.
        if (!manual) delay(SETTLE_MS)
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

    private fun explain(e: IOException): String = when (e) {
        is NearbyException ->
            "This phone couldn't use Bluetooth or Wi-Fi to look for the other phone. Check that Bluetooth, Wi-Fi and Location are on, then try again."
        else -> "The phones lost touch before they finished. Anything that arrived is kept; try again to send the rest."
    }

    private companion object {
        const val AFTER_RECORD_MS = 3_000L
        const val POLL_MS = 60_000L
        const val SETTLE_MS = 1_000L
    }
}
