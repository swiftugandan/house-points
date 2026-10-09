package dev.housepoints.sync

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpId
import dev.housepoints.contracts.Seq
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [OpLog] held in memory: for tests, previews and the e2e-mock suites. */
public class InMemoryOpLog : OpLog {
    private val mutex = Mutex()
    private val byId = LinkedHashMap<OpId, Op>()
    private val heads = HashMap<DeviceId, Seq>()
    private var highestLamport = Lamport.ZERO
    private val changeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val changes: Flow<Unit> = changeSignal.asSharedFlow()

    override suspend fun vector(): VersionVector = mutex.withLock { VersionVector(HashMap(heads)) }

    override suspend fun maxLamport(): Lamport = mutex.withLock { highestLamport }

    override suspend fun opsAfter(vector: VersionVector): List<Op> = mutex.withLock {
        byId.values.filterNot(vector::covers).sortedWith(Op.ORIGIN_ORDER)
    }

    override suspend fun append(ops: List<Op>): AppendResult {
        val plan = mutex.withLock {
            val plan = AppendPlanner.plan(ops, headOf = { heads[it] ?: Seq.NONE }, isStored = byId::containsKey)
            plan.accepted.forEach { op ->
                byId[op.opId] = op
                heads[op.originDevice] = op.originSeq
                if (op.lamport > highestLamport) highestLamport = op.lamport
            }
            plan
        }
        if (plan.accepted.isNotEmpty()) changeSignal.emit(Unit)
        return plan.result
    }

    override suspend fun all(): List<Op> = mutex.withLock { byId.values.toList() }
}
