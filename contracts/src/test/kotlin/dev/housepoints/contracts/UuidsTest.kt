package dev.housepoints.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class UuidsTest {
    @Test
    fun `v5 matches the RFC 9562 appendix A4 vector`() {
        val dnsNamespace = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        assertEquals(UUID.fromString("2ed6657d-e927-568b-95e1-2665a8aea6a2"), Uuids.v5(dnsNamespace, "www.example.com"))
    }

    @Test
    fun `v7 matches the RFC 9562 appendix A6 vector`() {
        val expected = UUID.fromString("017f22e2-79b0-7cc3-98c4-dc0c0c07398f")
        assertEquals(expected, Uuids.v7(0x017F22E279B0L, 0xCC3, 0x18C4DC0C0C07398FL))
    }

    @Test
    fun `v7 ids sort by creation time`() {
        val earlier = Uuids.v7(1_000L)
        val later = Uuids.v7(2_000L)
        assertTrue(Uuids.compare(earlier, later) < 0)
        assertEquals(7, later.version())
    }

    @Test
    fun `comparison is unsigned so ids with the top bit set sort last`() {
        val low = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val high = UUID.fromString("f0000000-0000-0000-0000-000000000000")
        assertTrue(Uuids.compare(low, high) < 0)
        assertTrue(low > high) // java.util.UUID's own signed order disagrees; that is why Uuids.compare exists
    }

    @Test
    fun `the same chore occurrence gets the same id on every device`() {
        val chore = ChoreId(UUID.fromString("11111111-1111-4111-8111-111111111111"))
        val child = ChildId(UUID.fromString("22222222-2222-4222-8222-222222222222"))
        val day = LocalDate.of(2026, 10, 13)
        assertEquals(EntryIds.recurringChore(chore, child, day), EntryIds.recurringChore(chore, child, day))
        assertNotEquals(EntryIds.recurringChore(chore, child, day), EntryIds.recurringChore(chore, child, day.plusDays(1)))
        assertNotEquals(EntryIds.onceChore(chore, child), EntryIds.recurringChore(chore, child, day))
    }

    @Test
    fun `a reversal of a reversal has its own id`() {
        val original = EntryId(Uuids.v7(5L))
        val reversal = EntryIds.reversal(original)
        assertEquals(reversal, EntryIds.reversal(original))
        assertNotEquals(reversal, EntryIds.reversal(reversal))
    }
}
