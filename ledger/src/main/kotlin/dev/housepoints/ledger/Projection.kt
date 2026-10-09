package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChildUpsert
import dev.housepoints.contracts.ChoreId
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.ChoreUpsert
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DeviceRemoved
import dev.housepoints.contracts.DeviceUpsert
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.EntryId
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.FamilyCreated
import dev.housepoints.contracts.GoalId
import dev.housepoints.contracts.GoalStatus
import dev.housepoints.contracts.GoalUpsert
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.MalformedPayload
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.Payload
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Policy
import dev.housepoints.contracts.PolicySet
import dev.housepoints.contracts.Recurrence
import dev.housepoints.contracts.TickSet
import dev.housepoints.contracts.UnknownPayload
import dev.housepoints.contracts.Uuids
import dev.housepoints.contracts.ValueId
import dev.housepoints.contracts.ValueUpsert

/** An op with its payload decoded once; ops are immutable, so callers can keep these across projections. */
public data class DecodedOp(val op: Op, val payload: Payload) {
    public companion object {
        public fun of(op: Op): DecodedOp = DecodedOp(op, OpCodec.decodePayload(op))
    }
}

/** `(ops, asOf) → FamilyState` (SAD §3.2). Pure: no clock, no I/O, no floating point. */
public object Projection {
    private val DEFAULT_ICON = IconKey("star")

    public fun project(ops: Collection<Op>, asOf: InstantMs): FamilyState = projectDecoded(ops.map(DecodedOp::of), asOf)

    /** As [project], for callers that keep decoded ops (decoding is the most expensive step). */
    public fun projectDecoded(ops: Collection<DecodedOp>, asOf: InstantMs): FamilyState {
        val folded = Fold()
        canonicalOrder(ops).forEach(folded::apply)
        val family = folded.family
        val canonicalEntries = folded.canonicalEntries()
        val accounts = if (family == null) AccountsResult.EMPTY else Accounts.calculate(
            Periods(family.zone, family.weekStart),
            canonicalEntries,
            folded.policies(),
            folded.childIdsInCreationOrder(),
            asOf,
        )
        val flags = accounts.flags + folded.malformed.sortedBy { it.op } +
            listOfNotNull(folded.unknownCount.takeIf { it > 0 }?.let { Flag.NewerVersionSeen(it) })
        return FamilyState(
            asOf = asOf,
            family = family,
            devices = folded.devices(),
            children = folded.children(),
            chores = folded.chores(),
            values = folded.values(),
            goals = folded.goals(),
            ticks = folded.ticks(),
            policies = folded.policies(),
            accounts = accounts.accounts,
            flags = flags,
        )
    }

    /** Duplicates removed and a single total order fixed, so input order and repetition cannot matter. */
    private val ORDER: Comparator<DecodedOp> = Comparator<DecodedOp> { a, b -> Op.CAUSAL_ORDER.compare(a.op, b.op) }
        .thenComparator { a, b -> Uuids.compare(a.op.opId.uuid, b.op.opId.uuid) }
        .thenBy { it.op.body }

    private fun canonicalOrder(ops: Collection<DecodedOp>): List<DecodedOp> = ops.sortedWith(ORDER).distinctBy { it.op.opId }

    /** Mutable accumulator used only inside [project]; never escapes. */
    private class Fold {
        var family: FamilySettings? = null
        var unknownCount: Int = 0
        val malformed = mutableListOf<Flag.MalformedEntry>()

        private val firstSeen = LinkedHashMap<Any, OpKey>()
        private val deviceNames = HashMap<DeviceId, Lww<String>>()
        private val removedDevices = HashSet<DeviceId>()
        private val childFields = HashMap<ChildId, ChildFields>()
        private val choreFields = HashMap<ChoreId, ChoreFields>()
        private val valueFields = HashMap<ValueId, ValueFields>()
        private val goalFields = HashMap<GoalId, GoalFields>()
        private val tickFields = HashMap<TickKey, Lww<Boolean>>()
        private val entries = LinkedHashMap<EntryId, CanonicalEntry>()
        private val exchange = mutableListOf<Pair<PolicyChange<Policy.Exchange>, OpKey>>()
        private val interest = mutableListOf<Pair<PolicyChange<Policy.Interest>, OpKey>>()
        private val penalty = mutableListOf<Pair<PolicyChange<Policy.Penalty>, OpKey>>()
        private val minCashOut = mutableListOf<Pair<PolicyChange<Policy.MinCashOut>, OpKey>>()

        fun apply(decoded: DecodedOp) {
            val op = decoded.op
            val key = OpKey.of(op)
            seen(op.originDevice, key)
            when (val payload = decoded.payload) {
                is FamilyCreated -> if (family == null) {
                    family = FamilySettings(op.familyId, payload.name, payload.currency, payload.zone, payload.weekStart)
                }
                is DeviceUpsert -> {
                    seen(payload.deviceId, key)
                    deviceNames.getOrPut(payload.deviceId) { Lww() }.offer(payload.name, key)
                }
                is DeviceRemoved -> {
                    seen(payload.deviceId, key)
                    removedDevices += payload.deviceId
                }
                is ChildUpsert -> {
                    seen(payload.childId, key)
                    childFields.getOrPut(payload.childId) { ChildFields() }.offer(payload, key)
                }
                is ChoreUpsert -> {
                    seen(payload.choreId, key)
                    choreFields.getOrPut(payload.choreId) { ChoreFields() }.offer(payload, key)
                }
                is ValueUpsert -> {
                    seen(payload.valueId, key)
                    valueFields.getOrPut(payload.valueId) { ValueFields() }.offer(payload, key)
                }
                is GoalUpsert -> {
                    seen(payload.goalId, key)
                    goalFields.getOrPut(payload.goalId) { GoalFields() }.offer(payload, key)
                }
                is TickSet -> tickFields.getOrPut(TickKey(payload.choreId, payload.childId, payload.day)) { Lww() }.offer(payload.done, key)
                is EntryRecorded -> if (payload.entryId !in entries) {
                    seen(payload.childId, key)
                    // Ops arrive in ascending OpKey order, so the first one seen is canonical (SPEC FR-19).
                    entries[payload.entryId] = CanonicalEntry(payload, op, key)
                }
                is PolicySet -> addPolicy(payload, op, key)
                is UnknownPayload -> unknownCount++
                is MalformedPayload -> malformed += Flag.MalformedEntry(op.opId, "unreadable ${payload.type}: ${payload.reason}")
            }
        }

        private fun addPolicy(set: PolicySet, op: Op, key: OpKey) {
            fun <P : Policy> change(policy: P) = PolicyChange(policy, set.effectiveFrom, op.originDevice, op.lamport, superseded = false) to key
            when (val policy = set.policy) {
                is Policy.Exchange -> exchange += change(policy)
                is Policy.Interest -> interest += change(policy)
                is Policy.Penalty -> penalty += change(policy)
                is Policy.MinCashOut -> minCashOut += change(policy)
            }
        }

        private fun seen(id: Any, key: OpKey) {
            firstSeen.putIfAbsent(id, key)
        }

        private fun <T : Any> creationOrder(ids: Collection<T>): List<T> =
            ids.sortedWith(compareBy<T> { firstSeen.getValue(it) }.thenComparator { a, b -> a.toString().compareTo(b.toString()) })

        fun canonicalEntries(): List<CanonicalEntry> = entries.values.toList()

        fun policies(): PolicyTimeline = PolicyTimeline(
            PolicyTimeline.resolve(exchange),
            PolicyTimeline.resolve(interest),
            PolicyTimeline.resolve(penalty),
            PolicyTimeline.resolve(minCashOut),
        )

        fun childIdsInCreationOrder(): List<ChildId> =
            creationOrder((childFields.keys + entries.values.map { it.entry.childId }).toSet())

        fun devices(): List<DeviceRecord> = creationOrder(firstSeen.keys.filterIsInstance<DeviceId>()).map { id ->
            DeviceRecord(id, deviceNames[id]?.value ?: "", id in removedDevices)
        }

        fun children(): List<ChildRecord> = creationOrder(childFields.keys).map { id ->
            val f = childFields.getValue(id)
            ChildRecord(
                id, f.name.value ?: "", f.colorIndex.value ?: 0, f.displayStyle.value ?: DisplayStyle.NUMBER,
                f.archived.value ?: false,
            )
        }

        fun chores(): List<ChoreRecord> = creationOrder(choreFields.keys).map { id ->
            val f = choreFields.getValue(id)
            ChoreRecord(
                id, f.title.value ?: "", f.icon.value ?: DEFAULT_ICON, f.kind.value ?: ChoreKind.ASSIGNED,
                f.points.value ?: Points.ZERO, f.assignees.value ?: emptySet(), f.recurrence.value ?: Recurrence.Once,
                f.archived.value ?: false,
            )
        }

        fun values(): List<ValueRecord> = creationOrder(valueFields.keys).map { id ->
            val f = valueFields.getValue(id)
            ValueRecord(id, f.name.value ?: "", f.icon.value ?: DEFAULT_ICON, f.archived.value ?: false)
        }

        fun goals(): List<GoalRecord> = creationOrder(goalFields.keys).mapNotNull { id ->
            val f = goalFields.getValue(id)
            val child = f.childId.value ?: return@mapNotNull null
            GoalRecord(
                id, child, f.title.value ?: "", f.icon.value ?: DEFAULT_ICON, f.target.value ?: Points.ZERO,
                f.status.value ?: GoalStatus.ACTIVE,
            )
        }

        fun ticks(): Map<TickKey, Boolean> = tickFields.mapNotNull { (k, v) -> v.value?.let { k to it } }.toMap()
    }

    private class ChildFields {
        val name = Lww<String>()
        val colorIndex = Lww<Int>()
        val displayStyle = Lww<DisplayStyle>()
        val archived = Lww<Boolean>()

        fun offer(p: ChildUpsert, key: OpKey) {
            name.offer(p.name, key)
            colorIndex.offer(p.colorIndex, key)
            displayStyle.offer(p.displayStyle, key)
            archived.offer(p.archived, key)
        }
    }

    private class ChoreFields {
        val title = Lww<String>()
        val icon = Lww<IconKey>()
        val kind = Lww<ChoreKind>()
        val points = Lww<Points>()
        val assignees = Lww<Set<ChildId>>()
        val recurrence = Lww<Recurrence>()
        val archived = Lww<Boolean>()

        fun offer(p: ChoreUpsert, key: OpKey) {
            title.offer(p.title, key)
            icon.offer(p.icon, key)
            kind.offer(p.kind, key)
            points.offer(p.points, key)
            assignees.offer(p.assignees, key)
            recurrence.offer(p.recurrence, key)
            archived.offer(p.archived, key)
        }
    }

    private class ValueFields {
        val name = Lww<String>()
        val icon = Lww<IconKey>()
        val archived = Lww<Boolean>()

        fun offer(p: ValueUpsert, key: OpKey) {
            name.offer(p.name, key)
            icon.offer(p.icon, key)
            archived.offer(p.archived, key)
        }
    }

    private class GoalFields {
        val childId = Lww<ChildId>()
        val title = Lww<String>()
        val icon = Lww<IconKey>()
        val target = Lww<Points>()
        val status = Lww<GoalStatus>()

        fun offer(p: GoalUpsert, key: OpKey) {
            childId.offer(p.childId, key)
            title.offer(p.title, key)
            icon.offer(p.icon, key)
            target.offer(p.target, key)
            status.offer(p.status, key)
        }
    }
}

/** The op that won for an entry id (SPEC FR-19). */
internal data class CanonicalEntry(val entry: EntryRecorded, val op: Op, val key: OpKey)
