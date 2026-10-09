package dev.housepoints.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.sync.FamilyKey
import dev.housepoints.sync.RejectReason
import dev.housepoints.sync.SyncOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceStoresTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun the_device_identity_is_stable_and_outside_backups() {
        val first = DeviceIdentityStore(context).deviceId()
        val second = DeviceIdentityStore(context).deviceId()
        assertEquals(first, second)
        assertTrue(File(context.noBackupFilesDir, "device-id").exists())
    }

    @Test
    fun the_family_key_round_trips_through_the_keystore() {
        val vault = FamilyKeyVault(context)
        vault.clear()
        assertNull(vault.load())
        val family = FamilyId(UUID.randomUUID())
        val key = FamilyKey.generate()
        vault.save(family, key)
        val loaded = FamilyKeyVault(context).load()
        assertEquals(family, loaded?.first)
        assertArrayEquals(key.bytes(), loaded?.second?.bytes())
        val stored = File(context.noBackupFilesDir, "family-key").readBytes()
        assertTrue("the key must not be stored in the clear", !stored.toList().windowed(32).any { it.toByteArray().contentEquals(key.bytes()) })
        vault.clear()
        assertNull(vault.load())
    }

    @Test
    fun sync_history_keeps_the_newest_twenty() = runBlocking {
        val store = SyncHistoryStore(context)
        val peer = DeviceId(UUID.randomUUID())
        repeat(25) { n ->
            val outcome = if (n % 2 == 0) {
                SyncOutcome.Completed(peer, n, 0, InstantMs(n.toLong()), InstantMs(n + 1L))
            } else {
                SyncOutcome.Rejected(RejectReason.TAMPERED, "n$n")
            }
            store.add(SyncHistoryEntry.of(outcome, peer, InstantMs(n.toLong()), InstantMs(n + 1L)))
        }
        val entries = SyncHistoryStore(context).entries()
        assertEquals(SyncHistoryStore.CAPACITY, entries.size)
        assertEquals(InstantMs(24), entries.first().startedAt)
        assertEquals(SyncHistoryEntry.Result.COMPLETED, entries.first().result)
        assertEquals(SyncHistoryEntry.Result.REJECTED, entries[1].result)
        assertEquals("TAMPERED: n23", entries[1].detail)
    }
}
