package dev.housepoints.contracts

/**
 * The unit of replication (SAD §4.1). Immutable once created; identified by [opId]; ordered for conflict
 * resolution by `(lamport, originDevice)` and never by wall-clock time (SPEC FR-33).
 *
 * [body] is the payload's UTF-8 JSON exactly as the originating device wrote it. The envelope never
 * re-encodes it, so unknown payload types survive any number of hops unchanged (SPEC FR-39).
 */
public data class Op(
    val opId: OpId,
    val familyId: FamilyId,
    val originDevice: DeviceId,
    val originSeq: Seq,
    val lamport: Lamport,
    val schemaVersion: Int,
    val type: String,
    val body: String,
) {
    public companion object {
        public const val CURRENT_SCHEMA: Int = 1

        /** The total order used for every last-writer-wins and canonical-entry decision. */
        public val CAUSAL_ORDER: Comparator<Op> = Comparator { a, b ->
            val byLamport = a.lamport.compareTo(b.lamport)
            if (byLamport != 0) byLamport else Uuids.compare(a.originDevice.uuid, b.originDevice.uuid)
        }

        /** Storage and transfer order: per device, by sequence. */
        public val ORIGIN_ORDER: Comparator<Op> = Comparator { a, b ->
            val byDevice = Uuids.compare(a.originDevice.uuid, b.originDevice.uuid)
            if (byDevice != 0) byDevice else a.originSeq.compareTo(b.originSeq)
        }
    }
}
