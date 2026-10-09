package dev.housepoints.contracts

import kotlinx.serialization.Serializable

/**
 * Whole points: the unit children see and the unit every ledger entry is recorded in (SPEC FR-17).
 * Arithmetic is checked: an overflow throws [ArithmeticException] and is never a wrapped value.
 */
@JvmInline @Serializable
public value class Points(public val value: Long) : Comparable<Points> {
    public operator fun plus(other: Points): Points = Points(Math.addExact(value, other.value))
    public operator fun minus(other: Points): Points = Points(Math.subtractExact(value, other.value))
    public operator fun unaryMinus(): Points = Points(Math.negateExact(value))
    public fun toMicropoints(): Micropoints = Micropoints(Math.multiplyExact(value, Micropoints.PER_POINT))
    override fun compareTo(other: Points): Int = value.compareTo(other.value)
    override fun toString(): String = "$value pts"

    public companion object {
        public val ZERO: Points = Points(0)
    }
}

/** The internal unit: 1 point = 1,000,000 µpt (SPEC glossary). All interest arithmetic happens here. */
@JvmInline @Serializable
public value class Micropoints(public val value: Long) : Comparable<Micropoints> {
    public operator fun plus(other: Micropoints): Micropoints = Micropoints(Math.addExact(value, other.value))
    public operator fun minus(other: Micropoints): Micropoints = Micropoints(Math.subtractExact(value, other.value))

    /** Whole points shown to people: rounded toward negative infinity (SPEC FR-27). */
    public fun floorPoints(): Points = Points(Math.floorDiv(value, PER_POINT))

    override fun compareTo(other: Micropoints): Int = value.compareTo(other.value)
    override fun toString(): String = "$value µpt"

    public companion object {
        public const val PER_POINT: Long = 1_000_000L
        public val ZERO: Micropoints = Micropoints(0)
    }
}

/** Basis points per period: 100 bp = 1% a week. */
@JvmInline @Serializable
public value class RateBp(public val value: Int) {
    public val isValid: Boolean get() = value in 0..MAX

    public companion object {
        public const val PER_UNIT: Long = 10_000L
        public const val MAX: Int = 10_000
    }
}

/** Milliseconds since the Unix epoch, UTC. */
@JvmInline @Serializable
public value class InstantMs(public val value: Long) : Comparable<InstantMs> {
    override fun compareTo(other: InstantMs): Int = value.compareTo(other.value)
    public fun toInstant(): java.time.Instant = java.time.Instant.ofEpochMilli(value)

    public companion object {
        public fun of(instant: java.time.Instant): InstantMs = InstantMs(instant.toEpochMilli())
    }
}

/** Lamport clock value [SPEC R7]. */
@JvmInline @Serializable
public value class Lamport(public val value: Long) : Comparable<Lamport> {
    override fun compareTo(other: Lamport): Int = value.compareTo(other.value)
    public fun next(): Lamport = Lamport(Math.addExact(value, 1L))

    public companion object {
        public val ZERO: Lamport = Lamport(0)
    }
}

/** Per-device contiguous sequence number, starting at 1. */
@JvmInline @Serializable
public value class Seq(public val value: Long) : Comparable<Seq> {
    override fun compareTo(other: Seq): Int = value.compareTo(other.value)
    public fun next(): Seq = Seq(Math.addExact(value, 1L))

    public companion object {
        public val NONE: Seq = Seq(0)
    }
}

/** ISO 4217 alphabetic code [SPEC R2]. */
@JvmInline @Serializable
public value class CurrencyCode(public val code: String) {
    public fun toCurrency(): java.util.Currency? = runCatching { java.util.Currency.getInstance(code) }.getOrNull()
}

/** Amount of money in the currency's minor unit (pence, cents). */
@JvmInline @Serializable
public value class MinorUnits(public val value: Long) : Comparable<MinorUnits> {
    override fun compareTo(other: MinorUnits): Int = value.compareTo(other.value)
}

/** `points` points are worth `minorUnits` minor units (SPEC FR-14). Both strictly positive. */
@Serializable
public data class ExchangeRate(val points: Long, val minorUnits: Long) {
    val isValid: Boolean get() = points > 0 && minorUnits > 0

    /** The money an amount of points converts to, or null when it does not convert exactly. */
    public fun moneyFor(amount: Points): MinorUnits? {
        val product = Math.multiplyExact(amount.value, minorUnits)
        return if (product % points == 0L) MinorUnits(product / points) else null
    }
}

/** Key into the app's built-in icon set; an open set so newer versions can add icons. */
@JvmInline @Serializable
public value class IconKey(public val key: String)

@Serializable
public enum class DisplayStyle { PICTURE, NUMBER }

@Serializable
public enum class ChoreKind { EXPECTED, ASSIGNED, BOUNTY }

@Serializable
public enum class PenaltyMode { NONE, CURRENT_WEEK, ANY }

@Serializable
public enum class GoalStatus { ACTIVE, REACHED, ABANDONED }

/** SPEC FR-17. The sign of [EntryRecorded.points] is fixed by the kind, except for adjustments. */
@Serializable
public enum class EntryKind { CHORE, AWARD, DEDUCTION, CASH_OUT, ADJUSTMENT, REVERSAL }
