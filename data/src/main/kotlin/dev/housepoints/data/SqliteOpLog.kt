package dev.housepoints.data

import android.content.Context
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import dev.housepoints.sync.AppendResult
import dev.housepoints.sync.OpLog
import dev.housepoints.sync.VersionVector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

/** The op log in a framework SQLite database (SAD §3.4, ADR-2). */
public class SqliteOpLog(context: Context, name: String = DEFAULT_NAME) : OpLog, Closeable {
    override suspend fun vector(): VersionVector = TODO("red")
    override suspend fun maxLamport(): Lamport = TODO("red")
    override suspend fun opsAfter(vector: VersionVector): List<Op> = TODO("red")
    override suspend fun append(ops: List<Op>): AppendResult = TODO("red")
    override suspend fun all(): List<Op> = TODO("red")
    override val changes: Flow<Unit> = emptyFlow()
    override fun close(): Unit = TODO("red")

    public companion object {
        public const val DEFAULT_NAME: String = "ops.db"
    }
}
