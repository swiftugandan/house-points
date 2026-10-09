package dev.housepoints.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChildUpsert
import dev.housepoints.sync.FamilyKey
import dev.housepoints.sync.SimDevice
import dev.housepoints.sync.SyncOutcome
import dev.housepoints.sync.TestOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** e2e with a mocked peer: a phone-side SQLite log syncing with an in-memory peer over loopback. */
@RunWith(AndroidJUnit4::class)
class SqliteSyncE2eTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    private val logs = mutableListOf<SqliteOpLog>()

    private fun sqliteLog(): SqliteOpLog =
        SqliteOpLog(context, "e2e-${UUID.randomUUID()}.db".also { names += it }).also { logs += it }

    @After
    fun cleanUp() {
        logs.forEach(SqliteOpLog::close)
        names.forEach(context::deleteDatabase)
    }

    @Test
    fun a_sqlite_phone_and_an_in_memory_peer_converge() = runBlocking(Dispatchers.Default) {
        val key = FamilyKey.generate()
        val phone = SimDevice(TestOps.device(1), key, log = sqliteLog())
        val peer = SimDevice(TestOps.device(2), key)
        repeat(300) { phone.factory.record(listOf(ChildUpsert(ChildId(UUID.randomUUID()), name = "p$it"))) }
        repeat(40) { peer.factory.record(listOf(ChildUpsert(ChildId(UUID.randomUUID()), name = "q$it"))) }

        val (onPhone, onPeer) = SimDevice.sync(phone, peer)
        assertTrue("$onPhone", onPhone is SyncOutcome.Completed)
        assertTrue("$onPeer", onPeer is SyncOutcome.Completed)
        assertEquals(peer.log.all().toSet(), phone.log.all().toSet())
        assertEquals(peer.log.vector(), phone.log.vector())
    }

    @Test
    fun an_export_imported_into_a_new_phone_reproduces_the_log() = runBlocking(Dispatchers.Default) {
        val original = SimDevice(TestOps.device(1), FamilyKey.generate(), log = sqliteLog())
        repeat(120) { original.factory.record(listOf(ChildUpsert(ChildId(UUID.randomUUID()), name = "c$it"))) }

        val file = ExportCodec.encrypt(original.log.all(), "passphrase".toCharArray(), iterations = 10_000)
        val imported = ExportCodec.decrypt(file, "passphrase".toCharArray()) as ExportResult.Ok
        val fresh = sqliteLog()
        assertEquals(120, fresh.append(imported.ops).added)
        assertEquals(original.log.all().toSet(), fresh.all().toSet())
    }
}
