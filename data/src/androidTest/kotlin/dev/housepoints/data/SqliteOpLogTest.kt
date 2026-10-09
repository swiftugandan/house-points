package dev.housepoints.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.housepoints.contracts.Seq
import dev.housepoints.sync.OpLog
import dev.housepoints.sync.OpLogContract
import dev.housepoints.sync.TestOps
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** The shared OpLog contract, run against real SQLite on the device. */
@RunWith(AndroidJUnit4::class)
class SqliteOpLogTest : OpLogContract() {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    private val opened = mutableListOf<SqliteOpLog>()

    private fun open(name: String): SqliteOpLog = SqliteOpLog(context, name).also { opened += it }

    override fun newLog(): OpLog = open("contract-${UUID.randomUUID()}.db".also { names += it })

    @After
    fun cleanUp() {
        opened.forEach(SqliteOpLog::close)
        names.forEach(context::deleteDatabase)
    }

    @Test
    fun ops_survive_closing_and_reopening_the_database() = runBlocking {
        val name = "reopen-${UUID.randomUUID()}.db".also { names += it }
        val a = TestOps.device(1)
        val ops = TestOps.ops(a, 1L..50L)
        open(name).apply {
            append(ops)
            close()
        }
        val reopened = open(name)
        assertEquals(ops.toSet(), reopened.all().toSet())
        assertEquals(Seq(50), reopened.vector()[a])
        assertEquals(1, reopened.append(listOf(TestOps.op(a, 51))).added)
    }
}
