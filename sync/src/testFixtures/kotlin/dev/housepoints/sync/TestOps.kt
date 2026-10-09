package dev.housepoints.sync

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DeviceUpsert
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.OpId
import dev.housepoints.contracts.Seq
import dev.housepoints.contracts.Uuids
import java.util.UUID

/** Builders for hand-made ops in tests. */
public object TestOps {
    public val FAMILY: FamilyId = FamilyId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))

    public fun device(n: Int): DeviceId = DeviceId(UUID(0L, n.toLong()))

    /** An op from [device] with sequence [seq]; the Lamport value defaults to the sequence number. */
    public fun op(device: DeviceId, seq: Long, lamport: Long = seq, family: FamilyId = FAMILY): Op {
        val (type, body) = OpCodec.encodePayload(DeviceUpsert(device, name = "op $seq"))
        return Op(OpId(Uuids.v7(seq)), family, device, Seq(seq), Lamport(lamport), Op.CURRENT_SCHEMA, type, body)
    }

    public fun ops(device: DeviceId, seqs: LongRange): List<Op> = seqs.map { op(device, it) }
}
