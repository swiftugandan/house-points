package dev.housepoints.data

import android.content.Context
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.sync.SyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

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
            when (outcome) {
                is SyncOutcome.Completed ->
                    SyncHistoryEntry(outcome.peer, startedAt, endedAt, Result.COMPLETED, "", outcome.sent, outcome.received)
                is SyncOutcome.Rejected ->
                    SyncHistoryEntry(peer, startedAt, endedAt, Result.REJECTED, "${outcome.reason}: ${outcome.detail}", 0, 0)
                is SyncOutcome.Interrupted ->
                    SyncHistoryEntry(peer, startedAt, endedAt, Result.INTERRUPTED, outcome.detail, 0, outcome.received)
            }
    }
}

/**
 * The most recent [CAPACITY] sync attempts, newest first, kept in a small JSON file in `filesDir`.
 * Nothing in it ever leaves the device unless the parent's backup includes app files.
 */
public class SyncHistoryStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val serializer = ListSerializer(SyncHistoryEntry.serializer())

    public suspend fun add(entry: SyncHistoryEntry): Unit = lock.withLock {
        val updated = (listOf(entry) + readAll()).take(CAPACITY)
        withContext(Dispatchers.IO) { AtomicFiles.write(file, json.encodeToString(serializer, updated).toByteArray()) }
    }

    public suspend fun entries(): List<SyncHistoryEntry> = lock.withLock { readAll() }

    private suspend fun readAll(): List<SyncHistoryEntry> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: SerializationException) {
            emptyList() // Diagnostics only: an unreadable history starts again rather than failing the app.
        }
    }

    public companion object {
        public const val CAPACITY: Int = 20
        private const val FILE_NAME = "sync-history.json"
        private val json = Json { ignoreUnknownKeys = true }
        private val lock = Mutex()
    }
}
