package dev.housepoints.sync

import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** [OpLog] held in memory: for tests, previews and the e2e-mock suites. */
public class InMemoryOpLog : OpLog {
    override suspend fun vector(): VersionVector = TODO("red")
    override suspend fun maxLamport(): Lamport = TODO("red")
    override suspend fun opsAfter(vector: VersionVector): List<Op> = TODO("red")
    override suspend fun append(ops: List<Op>): AppendResult = TODO("red")
    override suspend fun all(): List<Op> = TODO("red")
    override val changes: Flow<Unit> = emptyFlow()
}
