package dev.housepoints.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class OpCodecTest {
    private val family = FamilyId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
    private val device = DeviceId(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"))
    private val child = ChildId(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"))
    private val chore = ChoreId(UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"))

    private fun opOf(payload: Payload, seq: Long = 1): Op {
        val (type, body) = OpCodec.encodePayload(payload)
        return Op(OpId(Uuids.v7(seq)), family, device, Seq(seq), Lamport(seq), Op.CURRENT_SCHEMA, type, body)
    }

    private val samples: List<Payload> = listOf(
        FamilyCreated("Our family", CurrencyCode("GBP"), ZoneId.of("Europe/London"), DayOfWeek.MONDAY),
        DeviceUpsert(device, name = "Sam's phone"),
        DeviceRemoved(device),
        ChildUpsert(child, name = "Ada", colorIndex = 0, displayStyle = DisplayStyle.NUMBER),
        ChoreUpsert(
            chore, title = "Bins", icon = IconKey("bin"), kind = ChoreKind.ASSIGNED, points = Points(20),
            assignees = setOf(child), recurrence = Recurrence.Weekdays(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)),
        ),
        ValueUpsert(ValueId(UUID.randomUUID()), name = "Kindness", icon = IconKey("heart")),
        GoalUpsert(GoalId(UUID.randomUUID()), childId = child, title = "Headphones", target = Points(1200), status = GoalStatus.ACTIVE),
        EntryRecorded(
            EntryIds.recurringChore(chore, child, LocalDate.of(2026, 10, 13)), child, EntryKind.CHORE, Points(20),
            InstantMs(1_760_000_000_000), chore = ChoreRef(chore, LocalDate.of(2026, 10, 13)),
        ),
        EntryRecorded(
            EntryId(Uuids.v7(9)), child, EntryKind.CASH_OUT, Points(-150), InstantMs(1_760_000_000_000),
            cashOut = CashOut(MinorUnits(150), CurrencyCode("GBP"), ExchangeRate(1, 1)),
        ),
        PolicySet(Policy.Interest(RateBp(100), Points(2000)), InstantMs(0)),
        PolicySet(Policy.Interest(RateBp(100), null), InstantMs(0)),
        PolicySet(Policy.Penalty(PenaltyMode.CURRENT_WEEK), InstantMs(0)),
        TickSet(chore, child, LocalDate.of(2026, 10, 13), done = true),
    )

    @Test
    fun `every payload type survives encode and decode`() {
        samples.forEach { payload -> assertEquals(payload, OpCodec.decodePayload(opOf(payload))) }
    }

    @Test
    fun `every op survives the envelope byte format`() {
        samples.forEachIndexed { index, payload ->
            val op = opOf(payload, seq = index + 1L)
            assertEquals(DecodeResult.Decoded(op), OpCodec.decode(OpCodec.encode(op)))
        }
    }

    @Test
    fun `upserts omit fields they do not write`() {
        val (_, body) = OpCodec.encodePayload(ChildUpsert(child, name = "Ada"))
        assertEquals("""{"childId":"$child","name":"Ada"}""", body)
    }

    @Test
    fun `an unknown type is kept verbatim and re-encodes to the same body`() {
        val op = Op(OpId(Uuids.v7(1)), family, device, Seq(1), Lamport(1), 2, "locked.savings", """{"x":1,"y":[2]}""")
        val payload = OpCodec.decodePayload(op)
        assertEquals(UnknownPayload("locked.savings", """{"x":1,"y":[2]}"""), payload)
        assertEquals("locked.savings" to """{"x":1,"y":[2]}""", OpCodec.encodePayload(payload))
    }

    @Test
    fun `a newer schema of a known type is treated as unknown`() {
        val known = opOf(DeviceRemoved(device))
        val newer = known.copy(schemaVersion = Op.CURRENT_SCHEMA + 1)
        assertTrue(OpCodec.decodePayload(newer) is UnknownPayload)
    }

    @Test
    fun `fields added by a newer version of a known type are ignored on read`() {
        val op = opOf(DeviceRemoved(device)).let { it.copy(body = it.body.dropLast(1) + ""","reason":"lost"}""") }
        assertEquals(DeviceRemoved(device), OpCodec.decodePayload(op))
    }

    @Test
    fun `a broken body of a known type decodes to MalformedPayload instead of throwing`() {
        val op = opOf(DeviceRemoved(device)).copy(body = """{"deviceId":"not-a-uuid"}""")
        assertTrue(OpCodec.decodePayload(op) is MalformedPayload)
    }

    @Test
    fun `truncated or padded envelopes are malformed, never exceptions`() {
        val bytes = OpCodec.encode(opOf(DeviceRemoved(device)))
        assertTrue(OpCodec.decode(bytes.copyOf(bytes.size - 3)) is DecodeResult.Malformed)
        assertTrue(OpCodec.decode(bytes + byteArrayOf(0)) is DecodeResult.Malformed)
        assertTrue(OpCodec.decode(byteArrayOf()) is DecodeResult.Malformed)
    }
}
