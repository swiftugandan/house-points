package dev.housepoints.ledger

import dev.housepoints.contracts.Micropoints
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.RateBp

/** The one interest formula (SPEC FR-25), shared by payday, projections and statements. */
public object Interest {
    /** `floor(clamp(base, 0, cap) × rate / 10 000)`: nothing below zero, so a child is never charged. */
    public fun on(base: Micropoints, rate: RateBp, cap: Points?): Micropoints {
        if (base.value <= 0L) return Micropoints.ZERO
        val capped = cap?.let { minOf(base.value, it.toMicropoints().value) } ?: base.value
        return Micropoints(Math.multiplyExact(capped, rate.value.toLong()) / RateBp.PER_UNIT)
    }
}
