package dev.housepoints.lan

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DeviceRemoved
import dev.housepoints.contracts.DeviceUpsert
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Uuids
import dev.housepoints.sync.FamilyKey
import dev.housepoints.sync.InMemoryOpLog
import dev.housepoints.sync.OpFactory
import dev.housepoints.sync.SyncOutcome
import dev.housepoints.sync.SyncSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Base64

/**
 * Live e2e of the local-network link against the real app on a phone (PLAN §4a). Skipped unless run with
 * `-Php.peer=host:port -Php.pairing=hp1:…` (the phone's advertised address and its pairing code).
 * It pulls the family's history, then pushes one op naming itself and one marking itself removed, so the
 * phone's Phones list shows it as a removed test peer afterwards.
 */
class LiveDesktopPeerTest {
    @Test
    fun `a desktop peer syncs both ways with the app over home wi-fi`() = runBlocking {
        val peer = System.getProperty("hp.peer").orEmpty()
        val pairing = System.getProperty("hp.pairing").orEmpty()
        assumeTrue("live peer not configured", peer.isNotBlank() && pairing.startsWith("hp1:"))
        val bytes = Base64.getUrlDecoder().decode(pairing.removePrefix("hp1:"))
        val buffer = ByteBuffer.wrap(bytes)
        val family = FamilyId(Uuids.read(buffer))
        val key = FamilyKey.of(ByteArray(KEY_BYTES).also { buffer.get(it) })!!
        val self = DeviceId(Uuids.random())
        val log = InMemoryOpLog()
        val clock = { InstantMs(System.currentTimeMillis()) }

        fun open() = SocketTransport(Socket().apply { connect(InetSocketAddress(peer.substringBeforeLast(':'), peer.substringAfterLast(':').toInt()), TIMEOUT_MS) })

        val pull = SyncSession(family, key, self, log, open(), clock).run()
        println("LIVE pull: $pull, ops now ${log.all().size}")
        assertTrue(pull is SyncOutcome.Completed && pull.received > 0)

        OpFactory(family, self, log, clock).record(listOf(DeviceUpsert(self, "Test peer (Mac)"), DeviceRemoved(self)))
        val push = SyncSession(family, key, self, log, open(), clock).run()
        println("LIVE push: $push")
        assertTrue(push is SyncOutcome.Completed && push.sent == 2)
    }

    private companion object {
        const val KEY_BYTES = 32
        const val TIMEOUT_MS = 5_000
    }
}
