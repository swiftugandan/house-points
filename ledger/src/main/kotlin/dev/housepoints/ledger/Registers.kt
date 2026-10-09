package dev.housepoints.ledger

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.Uuids

/** Position of an op in the conflict-resolution order (SPEC FR-33): `(lamport, originDevice)`. */
internal data class OpKey(val lamport: Lamport, val device: DeviceId) : Comparable<OpKey> {
    override fun compareTo(other: OpKey): Int {
        val byLamport = lamport.compareTo(other.lamport)
        return if (byLamport != 0) byLamport else Uuids.compare(device.uuid, other.device.uuid)
    }

    companion object {
        fun of(op: Op): OpKey = OpKey(op.lamport, op.originDevice)
    }
}

/** A last-writer-wins register (SPEC FR-35): the write with the greatest [OpKey] wins. */
internal class Lww<T : Any> {
    var value: T? = null
        private set
    private var writtenBy: OpKey? = null

    fun offer(candidate: T?, key: OpKey) {
        if (candidate == null) return
        val current = writtenBy
        if (current == null || key > current) {
            value = candidate
            writtenBy = key
        }
    }
}
