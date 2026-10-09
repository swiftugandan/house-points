package dev.housepoints.app.ui

import dev.housepoints.app.ui.format.Formats
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.data.SyncHistoryEntry
import dev.housepoints.ledger.FamilyState

/** The sync line on Home and the staleness notice before a cash-out (SPEC FR-38). */
data class LastSync(val line: String, val cashOutNotice: String?)

/** Human wording for "when did this phone last sync", shared by Home, Sync, Phones and Record. */
object Relative {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun lastSync(state: FamilyState, history: List<SyncHistoryEntry>, self: DeviceId, now: InstantMs): LastSync {
        val others = state.devices.filter { !it.removed && it.id != self }
        if (others.isEmpty()) return LastSync("Only this phone so far", null)
        val latest = history.filter { it.result == SyncHistoryEntry.Result.COMPLETED && it.peer != null }.maxByOrNull { it.endedAt.value }
            ?: return LastSync("Not synced with another phone yet", "This phone hasn't synced with another phone yet.")
        val name = state.devices.firstOrNull { it.id == latest.peer }?.name?.ifBlank { null } ?: "the other phone"
        val ago = ago(latest.endedAt, now)
        val stale = now.value - latest.endedAt.value > DAY
        return LastSync("Synced with $name $ago", if (stale) "Last synced with $name $ago." else null)
    }

    fun lastSyncedWith(history: List<SyncHistoryEntry>, device: DeviceId, now: InstantMs): String? =
        history.filter { it.peer == device && it.result == SyncHistoryEntry.Result.COMPLETED }.maxByOrNull { it.endedAt.value }
            ?.let { "Last synced ${ago(it.endedAt, now)}" }

    fun historyLine(entry: SyncHistoryEntry, state: FamilyState, formats: Formats): String {
        val who = entry.peer?.let { peer -> state.devices.firstOrNull { it.id == peer }?.name?.ifBlank { null } } ?: "another phone"
        val day = formats.shortDay(entry.endedAt) + " " + formats.time(entry.endedAt)
        return when (entry.result) {
            SyncHistoryEntry.Result.COMPLETED -> "$day · $who · sent ${entry.sent}, received ${entry.received}"
            SyncHistoryEntry.Result.REJECTED -> "$day · refused"
            SyncHistoryEntry.Result.INTERRUPTED -> "$day · interrupted after ${entry.received} received"
        }
    }

    fun ago(then: InstantMs, now: InstantMs): String {
        val delta = (now.value - then.value).coerceAtLeast(0)
        return when {
            delta < MINUTE -> "just now"
            delta < HOUR -> plural(delta / MINUTE, "minute") + " ago"
            delta < DAY -> plural(delta / HOUR, "hour") + " ago"
            else -> plural(delta / DAY, "day") + " ago"
        }
    }

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
}
