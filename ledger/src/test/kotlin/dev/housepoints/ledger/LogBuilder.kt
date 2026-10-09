package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChildUpsert
import dev.housepoints.contracts.ChoreId
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.ChoreRef
import dev.housepoints.contracts.ChoreUpsert
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.EntryId
import dev.housepoints.contracts.EntryIds
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.ExchangeRate
import dev.housepoints.contracts.FamilyCreated
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.OpId
import dev.housepoints.contracts.Payload
import dev.housepoints.contracts.PenaltyMode
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Policy
import dev.housepoints.contracts.PolicySet
import dev.housepoints.contracts.RateBp
import dev.housepoints.contracts.Recurrence
import dev.housepoints.contracts.Seq
import dev.housepoints.contracts.Uuids
import dev.housepoints.contracts.CashOut
import dev.housepoints.contracts.MinorUnits
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

val LONDON: ZoneId = ZoneId.of("Europe/London")

/** `"2026-10-07T18:00"` in Europe/London. */
fun london(local: String): InstantMs = InstantMs.of(LocalDateTime.parse(local).atZone(LONDON).toInstant())

fun pts(value: Long): Points = Points(value)

/** Points with fractional part, e.g. `micro("204.02")`. */
fun micro(decimal: String): dev.housepoints.contracts.Micropoints =
    dev.housepoints.contracts.Micropoints(java.math.BigDecimal(decimal).movePointRight(6).longValueExact())

/**
 * Builds op logs the way phones would write them. Each device numbers its own ops; the Lamport clock is a
 * shared counter unless a test sets one explicitly to model phones that have not synced.
 */
class LogBuilder(seed: Long = 1) {
    private val random = java.util.Random(seed)
    val family = FamilyId(uuid())
    val phoneA = DeviceId(UUID.fromString("0aaaaaaa-0000-4000-8000-000000000001"))
    val phoneB = DeviceId(UUID.fromString("0bbbbbbb-0000-4000-8000-000000000002"))

    private val ops = mutableListOf<Op>()
    private val seqs = mutableMapOf<DeviceId, Long>()
    private var clock = 0L

    fun uuid(): UUID = UUID(random.nextLong(), random.nextLong())

    fun ops(): List<Op> = ops.toList()

    fun add(payload: Payload, device: DeviceId = phoneA, lamport: Long? = null): Op {
        val seq = (seqs[device] ?: 0L) + 1
        seqs[device] = seq
        clock = maxOf(clock, lamport ?: 0L) + if (lamport == null) 1 else 0
        val (type, body) = OpCodec.encodePayload(payload)
        val op = Op(
            OpId(Uuids.v7(1_700_000_000_000L + ops.size, random.nextInt(), random.nextLong())), family, device,
            Seq(seq), Lamport(lamport ?: clock), Op.CURRENT_SCHEMA, type, body,
        )
        ops += op
        return op
    }

    fun createFamily(name: String = "Test family") {
        add(FamilyCreated(name, CurrencyCode("GBP"), LONDON, DayOfWeek.MONDAY))
        add(PolicySet(Policy.Exchange(ExchangeRate(1, 1)), InstantMs(0)))
        add(PolicySet(Policy.Interest(RateBp(100), Points(2000)), InstantMs(0)))
        add(PolicySet(Policy.Penalty(PenaltyMode.CURRENT_WEEK), InstantMs(0)))
        add(PolicySet(Policy.MinCashOut(Points(100)), InstantMs(0)))
    }

    fun child(name: String, colorIndex: Int = 0, style: DisplayStyle = DisplayStyle.NUMBER): ChildId {
        val id = ChildId(uuid())
        add(ChildUpsert(id, name = name, colorIndex = colorIndex, displayStyle = style, archived = false))
        return id
    }

    fun chore(
        title: String,
        points: Long,
        kind: ChoreKind = ChoreKind.ASSIGNED,
        assignees: Set<ChildId> = emptySet(),
        recurrence: Recurrence = Recurrence.Daily,
    ): ChoreId {
        val id = ChoreId(uuid())
        add(ChoreUpsert(id, title, IconKey("bin"), kind, Points(points), assignees, recurrence, archived = false))
        return id
    }

    fun entry(
        child: ChildId,
        kind: EntryKind,
        points: Long,
        at: InstantMs,
        device: DeviceId = phoneA,
        id: EntryId = EntryId(Uuids.v7(at.value, random.nextInt(), random.nextLong())),
        lamport: Long? = null,
        note: String = "",
        chore: ChoreRef? = null,
        reverses: EntryId? = null,
        cashOut: CashOut? = null,
    ): EntryId {
        add(EntryRecorded(id, child, kind, Points(points), at, note, chore, null, reverses, cashOut), device, lamport)
        return id
    }

    fun chorePaid(child: ChildId, points: Long, at: String, device: DeviceId = phoneA, lamport: Long? = null): EntryId =
        entry(child, EntryKind.CHORE, points, london(at), device, lamport = lamport)

    fun award(child: ChildId, points: Long, at: String, device: DeviceId = phoneA, lamport: Long? = null): EntryId =
        entry(child, EntryKind.AWARD, points, london(at), device, lamport = lamport, note = "noticed")

    fun cashOut(child: ChildId, points: Long, at: String, device: DeviceId = phoneA, lamport: Long? = null): EntryId =
        entry(
            child, EntryKind.CASH_OUT, -points, london(at), device, lamport = lamport,
            cashOut = CashOut(MinorUnits(points), CurrencyCode("GBP"), ExchangeRate(1, 1)),
        )

    fun reverse(target: EntryId, child: ChildId, targetPoints: Long, at: InstantMs, device: DeviceId, lamport: Long? = null): EntryId =
        entry(
            child, EntryKind.REVERSAL, -targetPoints, at, device, id = EntryIds.reversal(target),
            lamport = lamport, note = "mistake", reverses = target,
        )

    fun interestRate(bp: Int, cap: Long? = 2000, from: String, device: DeviceId = phoneA, lamport: Long? = null) {
        add(PolicySet(Policy.Interest(RateBp(bp), cap?.let(::Points)), london(from)), device, lamport)
    }

    fun penalty(mode: PenaltyMode, from: InstantMs = InstantMs(0)) {
        add(PolicySet(Policy.Penalty(mode), from))
    }

    companion object {
        val OCT_5: LocalDate = LocalDate.of(2026, 10, 5)
    }
}
