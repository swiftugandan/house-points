package dev.housepoints.contracts

import kotlinx.serialization.Serializable
import java.util.UUID

/** Every identifier is a UUID with its own type, so an id can never be passed where another kind is expected. */
public sealed interface Id : Comparable<Id> {
    public val uuid: UUID
    override fun compareTo(other: Id): Int = Uuids.compare(uuid, other.uuid)
}

@JvmInline @Serializable
public value class FamilyId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class DeviceId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class ChildId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class ChoreId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class ValueId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class GoalId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class EntryId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

/** contracts 0.2.0. */
@JvmInline @Serializable
public value class RewardId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

@JvmInline @Serializable
public value class OpId(@Serializable(with = UuidSerializer::class) override val uuid: UUID) : Id {
    override fun toString(): String = uuid.toString()
}

/**
 * Deterministic entry ids (SPEC FR-18): the same real-world event recorded on two phones gets the same id,
 * so the log holds it once. Everything else gets a time-ordered random id.
 */
public object EntryIds {
    /** Fixed namespace for every House Points v5 id; never change it. */
    public val NAMESPACE: UUID = UUID.fromString("6f1d3c2a-8b47-4f0e-9a51-2c7e5d9b4a13")

    public fun recurringChore(chore: ChoreId, child: ChildId, occurrence: java.time.LocalDate): EntryId =
        EntryId(Uuids.v5(NAMESPACE, "chore:$chore:$child:$occurrence"))

    public fun onceChore(chore: ChoreId, child: ChildId): EntryId =
        EntryId(Uuids.v5(NAMESPACE, "chore:$chore:$child"))

    public fun reversal(of: EntryId): EntryId = EntryId(Uuids.v5(NAMESPACE, "reversal:$of"))

    /** contracts 0.2.0, SPEC FR-50: the maturity payout of a lock, the same on every phone. */
    public fun lockPayout(lock: EntryId): EntryId = EntryId(Uuids.v5(NAMESPACE, "lock-payout:$lock"))

    public fun random(nowMillis: Long): EntryId = EntryId(Uuids.v7(nowMillis))
}
