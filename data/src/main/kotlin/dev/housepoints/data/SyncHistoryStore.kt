package dev.housepoints.data

import android.content.Context
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.sync.SyncOutcome
import kotlinx.serialization.Serializable

/** One line of the diagnostics screen's sync history (NFR-OPS-3). */
@Serializable
public data class SyncHistoryEntry(
    val peer: DeviceId?,
    val startedAt: InstantMs,
    val endedAt: InstantMs,
    val result: Result,
    val detail: String,
    val sent: Int,
    val received: Int,
) {
    @Serializable
    public enum class Result { COMPLETED, REJECTED, INTERRUPTED }

    public companion object {
        public fun of(outcome: SyncOutcome, peer: DeviceId?, startedAt: InstantMs, endedAt: InstantMs): SyncHistoryEntry =
            TODO("red")
    }
}

/** The most recent [CAPACITY] sync attempts, newest first. */
public class SyncHistoryStore(context: Context) {
    public suspend fun add(entry: SyncHistoryEntry): Unit = TODO("red")
    public suspend fun entries(): List<SyncHistoryEntry> = TODO("red")

    public companion object {
        public const val CAPACITY: Int = 20
    }
}
