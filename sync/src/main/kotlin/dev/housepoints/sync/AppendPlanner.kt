package dev.housepoints.sync

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpId
import dev.housepoints.contracts.Seq

/**
 * The [OpLog.append] rule, shared by every implementation so they cannot drift: sort by
 * [Op.ORIGIN_ORDER], accept an op only when it is the next sequence number for its device, count ops
 * already held as duplicates, and skip ops that would leave a gap.
 */
public object AppendPlanner {
    public class Plan(public val accepted: List<Op>, public val duplicates: Int) {
        public val result: AppendResult get() = AppendResult(accepted.size, duplicates)
    }

    /**
     * @param headOf the stored head sequence for a device before this batch.
     * @param isStored whether an op id is already stored.
     */
    public fun plan(batch: List<Op>, headOf: (DeviceId) -> Seq, isStored: (OpId) -> Boolean): Plan {
        val heads = HashMap<DeviceId, Seq>()
        val acceptedIds = HashSet<OpId>()
        val accepted = ArrayList<Op>(batch.size)
        var duplicates = 0
        for (op in batch.sortedWith(Op.ORIGIN_ORDER)) {
            val head = heads.getOrPut(op.originDevice) { headOf(op.originDevice) }
            when {
                op.originSeq <= head || op.opId in acceptedIds || isStored(op.opId) -> duplicates++
                op.originSeq == head.next() -> {
                    accepted += op
                    acceptedIds += op.opId
                    heads[op.originDevice] = op.originSeq
                }
                else -> Unit // A gap: the missing ops arrive in a later sync, and this one with them.
            }
        }
        return Plan(accepted, duplicates)
    }
}
