package dev.housepoints.sync

import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import kotlinx.coroutines.flow.Flow

/** What an [OpLog.append] did: [added] new ops stored, [duplicates] already present. */
public data class AppendResult(val added: Int, val duplicates: Int)

/**
 * The durable, append-only set of ops (SAD §4.4). Implementations must be safe to call concurrently.
 *
 * Contiguity: for every origin device the stored sequence numbers are exactly `1..vector()[device]`.
 * [append] sorts the batch by [Op.ORIGIN_ORDER] and stores an op only when its sequence number is the
 * next one for its device. An op whose `opId` or `(originDevice, originSeq)` is already stored is a
 * duplicate. An op that would leave a gap is skipped and counted as neither; it arrives again in a later
 * sync, after the ops that precede it.
 */
public interface OpLog {
    /** Highest contiguous sequence number per origin device. */
    public suspend fun vector(): VersionVector

    /** Greatest Lamport value stored, or [Lamport.ZERO] for an empty log. */
    public suspend fun maxLamport(): Lamport

    /** Ops the holder of [vector] lacks, ascending by [Op.ORIGIN_ORDER]. */
    public suspend fun opsAfter(vector: VersionVector): List<Op>

    /** Atomic: either the whole result is stored or nothing is. */
    public suspend fun append(ops: List<Op>): AppendResult

    public suspend fun all(): List<Op>

    /** Emits after every [append] that stored at least one op. */
    public val changes: Flow<Unit>
}
