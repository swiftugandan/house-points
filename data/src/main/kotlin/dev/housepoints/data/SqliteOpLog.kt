package dev.housepoints.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpId
import dev.housepoints.contracts.Seq
import dev.housepoints.contracts.Uuids
import dev.housepoints.sync.AppendPlanner
import dev.housepoints.sync.AppendResult
import dev.housepoints.sync.OpLog
import dev.housepoints.sync.VersionVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.UUID

/**
 * The op log in a framework SQLite database (SAD §3.4, ADR-2): one append-only table, WAL journaling,
 * every [append] in one transaction. UUIDs are stored as 16-byte blobs, whose SQLite order (memcmp) is
 * the same unsigned order as [Uuids.compare], so `ORDER BY origin_device, origin_seq` is [Op.ORIGIN_ORDER].
 */
public class SqliteOpLog(context: Context, name: String = DEFAULT_NAME) : OpLog, Closeable {
    private val helper = Helper(context.applicationContext, name)
    private val writeLock = Mutex()
    private val changeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val changes: Flow<Unit> = changeSignal.asSharedFlow()

    override suspend fun vector(): VersionVector = onDisk { db -> VersionVector(heads(db)) }

    override suspend fun maxLamport(): Lamport = onDisk { db ->
        db.rawQuery("SELECT IFNULL(MAX(lamport), 0) FROM ops", null).use { cursor ->
            cursor.moveToFirst()
            Lamport(cursor.getLong(0))
        }
    }

    override suspend fun opsAfter(vector: VersionVector): List<Op> = onDisk { db ->
        heads(db).keys.sortedWith { a, b -> Uuids.compare(a.uuid, b.uuid) }.flatMap { device ->
            query(db, "WHERE origin_device = ${blobLiteral(device.uuid)} AND origin_seq > ${vector[device].value} ORDER BY origin_seq")
        }
    }

    override suspend fun append(ops: List<Op>): AppendResult = writeLock.withLock {
        val plan = onDisk { db ->
            db.beginTransaction()
            try {
                val heads = heads(db)
                val plan = AppendPlanner.plan(ops, headOf = { heads[it] ?: Seq.NONE }, isStored = { isStored(db, it) })
                plan.accepted.forEach { op -> db.insertOrThrow(TABLE, null, op.toRow()) }
                db.setTransactionSuccessful()
                plan
            } finally {
                db.endTransaction()
            }
        }
        if (plan.accepted.isNotEmpty()) changeSignal.emit(Unit)
        plan.result
    }

    override suspend fun all(): List<Op> = onDisk { db -> query(db, "ORDER BY origin_device, origin_seq") }

    override fun close() {
        helper.close()
    }

    private suspend fun <T> onDisk(block: (SQLiteDatabase) -> T): T =
        withContext(Dispatchers.IO) { block(helper.writableDatabase) }

    private fun heads(db: SQLiteDatabase): Map<DeviceId, Seq> =
        db.rawQuery("SELECT origin_device, MAX(origin_seq) FROM ops GROUP BY origin_device", null).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(DeviceId(uuid(cursor.getBlob(0))), Seq(cursor.getLong(1))) }
        }

    private fun isStored(db: SQLiteDatabase, id: OpId): Boolean =
        db.compileStatement("SELECT COUNT(*) FROM ops WHERE op_id = ?").use { statement ->
            statement.bindBlob(1, Uuids.toBytes(id.uuid))
            statement.simpleQueryForLong() > 0
        }

    /** [clause] may only contain values this class generated itself (numbers, [blobLiteral]s). */
    private fun query(db: SQLiteDatabase, clause: String): List<Op> =
        db.rawQuery("SELECT $COLUMNS FROM ops $clause", null).use { rows ->
            buildList { while (rows.moveToNext()) add(rows.toOp()) }
        }

    /**
     * `rawQuery` binds only strings, and a blob column never equals a string, so blob keys go into the
     * SQL as `X'…'` literals. The hex comes from a UUID, never from input text.
     */
    private fun blobLiteral(uuid: UUID): String =
        Uuids.toBytes(uuid).joinToString(separator = "", prefix = "X'", postfix = "'") { "%02x".format(it.toInt() and 0xff) }

    private fun Op.toRow(): ContentValues = ContentValues().apply {
        put("op_id", Uuids.toBytes(opId.uuid))
        put("family_id", Uuids.toBytes(familyId.uuid))
        put("origin_device", Uuids.toBytes(originDevice.uuid))
        put("origin_seq", originSeq.value)
        put("lamport", lamport.value)
        put("schema_version", schemaVersion)
        put("type", type)
        put("body", body)
    }

    private fun Cursor.toOp(): Op = Op(
        opId = OpId(uuid(getBlob(0))),
        familyId = FamilyId(uuid(getBlob(1))),
        originDevice = DeviceId(uuid(getBlob(2))),
        originSeq = Seq(getLong(3)),
        lamport = Lamport(getLong(4)),
        schemaVersion = getInt(5),
        type = getString(6),
        body = getString(7),
    )

    private fun uuid(blob: ByteArray): UUID = Uuids.read(ByteBuffer.wrap(blob))

    private class Helper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, SCHEMA_VERSION) {
        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE ops (
                    op_id BLOB PRIMARY KEY NOT NULL,
                    family_id BLOB NOT NULL,
                    origin_device BLOB NOT NULL,
                    origin_seq INTEGER NOT NULL,
                    lamport INTEGER NOT NULL,
                    schema_version INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    body TEXT NOT NULL,
                    UNIQUE (origin_device, origin_seq)
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit // Schema 1 is the only one.
    }

    public companion object {
        public const val DEFAULT_NAME: String = "ops.db"
        private const val SCHEMA_VERSION = 1
        private const val TABLE = "ops"
        private const val COLUMNS = "op_id, family_id, origin_device, origin_seq, lamport, schema_version, type, body"
    }
}
