package dev.housepoints.sync

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.Seq

/** Highest contiguous sequence number held per origin device. Immutable, with value equality. */
public class VersionVector(entries: Map<DeviceId, Seq>) {
    public val entries: Map<DeviceId, Seq> = entries.filterValues { it > Seq.NONE }

    public operator fun get(device: DeviceId): Seq = entries[device] ?: Seq.NONE

    public fun covers(op: Op): Boolean = TODO("red")

    public fun withOp(op: Op): VersionVector = TODO("red")

    override fun equals(other: Any?): Boolean = other is VersionVector && other.entries == entries
    override fun hashCode(): Int = entries.hashCode()
    override fun toString(): String = "VersionVector($entries)"

    public companion object {
        public val EMPTY: VersionVector = VersionVector(emptyMap())
    }
}
