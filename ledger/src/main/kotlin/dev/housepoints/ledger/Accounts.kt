package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.EntryId
import dev.housepoints.contracts.EntryIds
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Micropoints
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.RateBp
import dev.housepoints.contracts.Uuids
import java.util.TreeMap

internal data class AccountsResult(val accounts: Map<ChildId, Account>, val flags: List<Flag>) {
    companion object {
        val EMPTY = AccountsResult(emptyMap(), emptyList())
    }
}

/** Balances and interest per child (SPEC FR-21 to FR-29). */
internal object Accounts {
    private const val UUID_VERSION_RANDOM_TIME_ORDERED = 7

    fun calculate(
        periods: Periods,
        entries: List<CanonicalEntry>,
        policies: PolicyTimeline,
        childOrder: List<ChildId>,
        asOf: InstantMs,
    ): AccountsResult {
        val effects = Effects(entries.associateBy { it.entry.entryId })
        val linesByChild = entries.map { effects.line(it) }.groupBy { it.entry.childId }
        val accounts = LinkedHashMap<ChildId, Account>()
        val flags = mutableListOf<Flag>()
        for (child in childOrder) {
            val lines = linesByChild[child].orEmpty()
            val account = try {
                InterestEngine(periods, policies, asOf).account(child, lines)
            } catch (e: ArithmeticException) {
                flags += Flag.Overflow(child)
                continue
            }
            accounts[child] = account
            if (account.balance < Micropoints.ZERO) flags += Flag.Overdrawn(child, account.balance)
            flags += possibleDuplicates(child, lines, periods)
        }
        flags += effects.problems.sortedBy { it.op }
        return AccountsResult(accounts, flags)
    }

    /** SPEC FR-20: same child, kind, amount and local day, recorded on different phones, neither reversed. */
    private fun possibleDuplicates(child: ChildId, lines: List<LedgerLine>, periods: Periods): List<Flag.PossibleDuplicate> {
        val candidates = lines.filter {
            it.entry.kind != EntryKind.REVERSAL && it.reversedBy == null && it.effect != Points.ZERO &&
                it.entry.entryId.uuid.version() == UUID_VERSION_RANDOM_TIME_ORDERED
        }
        if (candidates.distinctBy { it.recordedBy }.size < 2) return emptyList()
        return candidates
            .groupBy { Triple(it.entry.kind, it.entry.points, periods.localDate(it.entry.effectiveAt)) }
            .values
            .flatMap { group ->
                group.flatMapIndexed { i, a ->
                    group.drop(i + 1).filter { it.recordedBy != a.recordedBy }.map { b ->
                        val (first, second) = listOf(a.entry.entryId, b.entry.entryId).sorted()
                        Flag.PossibleDuplicate(child, first, second)
                    }
                }
            }
            .distinct()
            .sortedWith(compareBy<Flag.PossibleDuplicate> { it.first }.thenBy { it.second })
    }
}

/**
 * What each canonical entry does. Reversals take the negation of their target's effect at the target's
 * instant (SPEC FR-16); a reversal whose target has not arrived yet does nothing until it does.
 */
internal class Effects(private val byId: Map<EntryId, CanonicalEntry>) {
    private data class Resolution(val effect: Points, val at: InstantMs, val valid: Boolean)

    /** Target id → its reversal, for reversals whose id is the deterministic one (SPEC FR-18). */
    private val reversalOf: Map<EntryId, EntryId> = byId.values
        .mapNotNull { c -> c.entry.reverses?.let { target -> target to c.entry.entryId } }
        .filter { (target, reversal) -> reversal == EntryIds.reversal(target) }
        .toMap()

    private val resolved = HashMap<EntryId, Resolution>()
    val problems = mutableListOf<Flag.MalformedEntry>()

    fun line(canonical: CanonicalEntry): LedgerLine {
        val resolution = resolve(canonical.entry.entryId, depth = 0)
        return LedgerLine(
            entry = canonical.entry.copy(effectiveAt = resolution.at),
            effect = resolution.effect,
            recordedBy = canonical.op.originDevice,
            lamport = canonical.op.lamport,
            reversedBy = reversalInForce(canonical.entry.entryId, depth = 0),
        )
    }

    /** The valid reversal of [id] that is not itself reversed, if any. */
    fun reversalInForce(id: EntryId, depth: Int): EntryId? {
        if (depth > MAX_CHAIN) return null
        val reversalId = reversalOf[id] ?: return null
        if (!resolve(reversalId, depth + 1).valid) return null
        return if (reversalInForce(reversalId, depth + 1) == null) reversalId else null
    }

    private fun resolve(id: EntryId, depth: Int): Resolution {
        resolved[id]?.let { return it }
        val canonical = byId.getValue(id)
        val entry = canonical.entry
        val points = entry.points.value
        val resolution = when (entry.kind) {
            EntryKind.CHORE, EntryKind.AWARD -> check(points > 0, canonical, "${entry.kind} must be positive")
            EntryKind.DEDUCTION, EntryKind.CASH_OUT -> check(points < 0, canonical, "${entry.kind} must be negative")
            EntryKind.ADJUSTMENT -> check(points != 0L, canonical, "adjustment must not be zero")
            EntryKind.REVERSAL -> resolveReversal(canonical, depth)
        }
        resolved[id] = resolution
        return resolution
    }

    private fun resolveReversal(canonical: CanonicalEntry, depth: Int): Resolution {
        val entry = canonical.entry
        val target = entry.reverses ?: return invalid(canonical, "reversal without a target")
        if (entry.entryId != EntryIds.reversal(target)) return invalid(canonical, "reversal id does not match its target")
        if (depth > MAX_CHAIN) return invalid(canonical, "reversal chain too deep")
        val targetEntry = byId[target] ?: return Resolution(Points.ZERO, entry.effectiveAt, valid = true)
        if (targetEntry.entry.childId != entry.childId) return invalid(canonical, "reversal for another child")
        val targetResolution = resolve(target, depth + 1)
        if (!targetResolution.valid) return Resolution(Points.ZERO, targetResolution.at, valid = true)
        return Resolution(-targetResolution.effect, targetResolution.at, valid = true)
    }

    private fun check(ok: Boolean, canonical: CanonicalEntry, reason: String): Resolution =
        if (ok) Resolution(canonical.entry.points, canonical.entry.effectiveAt, valid = true) else invalid(canonical, reason)

    private fun invalid(canonical: CanonicalEntry, reason: String): Resolution {
        problems += Flag.MalformedEntry(canonical.op.opId, reason)
        return Resolution(Points.ZERO, canonical.entry.effectiveAt, valid = false)
    }

    private companion object {
        const val MAX_CHAIN = 64
    }
}

/** The week-by-week fold for one child. Every number is an integer number of micropoints. */
internal class InterestEngine(
    private val periods: Periods,
    private val policies: PolicyTimeline,
    private val asOf: InstantMs,
) {
    /** Net change per instant (SPEC FR-22), and the in-force credits/debits used for statements. */
    private class Changes {
        val net = TreeMap<Long, Long>()
        val credits = TreeMap<Long, Long>()
        val debits = TreeMap<Long, Long>()
    }

    fun account(child: ChildId, lines: List<LedgerLine>): Account {
        val changes = collect(lines)
        val summaries = ArrayList<PeriodSummary>()
        var balance = Micropoints.ZERO
        val current = periods.containing(asOf)
        val firstInstant = changes.net.firstEntry()?.key
        if (firstInstant != null && firstInstant < current.start.value) {
            var period = periods.containing(InstantMs(firstInstant))
            while (period.end <= asOf) {
                val summary = settle(period, balance, changes)
                summaries += summary
                balance = summary.closing
                period = periods.next(period)
            }
        }
        val currentPeriod = inProgress(current, balance, changes)
        val ordered = lines.sortedWith(NEWEST_FIRST)
        return Account(child, balanceAt(currentPeriod, changes), ordered, summaries.asReversed().toList(), currentPeriod)
    }

    private fun collect(lines: List<LedgerLine>): Changes {
        val changes = Changes()
        for (line in lines) {
            if (line.effect == Points.ZERO) continue
            val at = line.entry.effectiveAt.value
            val micro = line.effect.toMicropoints().value
            changes.net.merge(at, micro, Math::addExact)
            val countsInStatement = line.entry.kind != EntryKind.REVERSAL && line.reversedBy == null
            if (countsInStatement) {
                val target = if (micro > 0) changes.credits else changes.debits
                target.merge(at, line.effect.value, Math::addExact)
            }
        }
        changes.net.values.removeIf { it == 0L }
        return changes
    }

    private fun settle(period: Period, opening: Micropoints, changes: Changes): PeriodSummary {
        var balance = opening.value
        var lowest = balance
        var lowestAt: Long? = null
        for ((instant, delta) in changes.net.subMap(period.start.value, period.end.value)) {
            balance = Math.addExact(balance, delta)
            if (balance < lowest) {
                lowest = balance
                lowestAt = instant
            }
        }
        val policy = policies.interestAt(period.start)
        val rate = policy?.rate ?: RateBp(0)
        val interest = interestOn(Micropoints(lowest), rate, policy?.cap)
        return PeriodSummary(
            start = period.start,
            end = period.end,
            startDate = period.startDate,
            opening = opening,
            credits = Points(sum(changes.credits, period.start.value, period.end.value)),
            debits = Points(sum(changes.debits, period.start.value, period.end.value)),
            lowest = Micropoints(lowest),
            lowestAt = lowestAt?.let(::InstantMs),
            rate = rate,
            cap = policy?.cap,
            interest = interest,
            closing = Micropoints(balance) + interest,
        )
    }

    private fun inProgress(period: Period, opening: Micropoints, changes: Changes): CurrentPeriod {
        var balance = opening.value
        var lowest = balance
        for (delta in changes.net.subMap(period.start.value, asOf.value).values) {
            balance = Math.addExact(balance, delta)
            lowest = minOf(lowest, balance)
        }
        val policy = policies.interestAt(period.start)
        val rate = policy?.rate ?: RateBp(0)
        return CurrentPeriod(
            start = period.start,
            end = period.end,
            startDate = period.startDate,
            opening = opening,
            credits = Points(sum(changes.credits, period.start.value, asOf.value)),
            debits = Points(sum(changes.debits, period.start.value, asOf.value)),
            lowestSoFar = Micropoints(lowest),
            rate = rate,
            cap = policy?.cap,
            expectedInterest = interestOn(Micropoints(lowest), rate, policy?.cap),
        )
    }

    private fun balanceAt(current: CurrentPeriod, changes: Changes): Micropoints =
        current.opening + Micropoints(changes.net.subMap(current.start.value, asOf.value).values.fold(0L, Math::addExact))

    private fun sum(map: TreeMap<Long, Long>, from: Long, to: Long): Long =
        if (from >= to) 0L else map.subMap(from, to).values.fold(0L, Math::addExact)

    companion object {
        private val NEWEST_FIRST: Comparator<LedgerLine> = Comparator { a, b ->
            val byInstant = b.entry.effectiveAt.compareTo(a.entry.effectiveAt)
            if (byInstant != 0) return@Comparator byInstant
            val byLamport = b.lamport.compareTo(a.lamport)
            if (byLamport != 0) byLamport else Uuids.compare(b.recordedBy.uuid, a.recordedBy.uuid)
        }

        /** SPEC FR-25: `floor(clamp(base, 0, cap) × rate / 10 000)`; nothing below zero, never charged. */
        fun interestOn(base: Micropoints, rate: RateBp, cap: Points?): Micropoints {
            if (base.value <= 0L) return Micropoints.ZERO
            val capped = cap?.let { minOf(base.value, it.toMicropoints().value) } ?: base.value
            return Micropoints(Math.multiplyExact(capped, rate.value.toLong()) / RateBp.PER_UNIT)
        }
    }
}
