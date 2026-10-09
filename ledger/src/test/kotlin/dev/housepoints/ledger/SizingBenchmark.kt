package dev.housepoints.ledger

import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.InstantMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NFR-PERF-1 guard. Timing depends on the machine, so this runs as `./gradlew :ledger:benchmark`, never as part
 * of `test`; CI runs it in a step that reports without failing the build.
 */
class SizingBenchmark {
    @Test
    fun `sizing dataset recalculates within budget (NFR-PERF-1)`() {
        val log = LogBuilder().apply { createFamily() }
        val children = List(8) { log.child("Child $it", colorIndex = it) }
        val start = london("2016-10-03T00:00").value
        val week = 7L * 24 * 3_600_000
        for (w in 0 until 520) {
            for (child in children) {
                repeat(40) { i ->
                    val at = InstantMs(start + w * week + i * 3_600_000L * 4)
                    if (i % 10 == 9) log.entry(child, EntryKind.CASH_OUT, -5, at)
                    else log.entry(child, EntryKind.CHORE, 3, at)
                }
            }
        }
        // The app decodes each op once when it arrives (ops are immutable); recalculation starts from decoded ops.
        val decoded = log.ops().map(DecodedOp::of)
        val asOf = InstantMs(start + 520 * week)
        repeat(WARM_UPS) { Projection.projectDecoded(decoded, asOf) }
        val timings = List(RUNS) {
            val began = System.nanoTime()
            Projection.projectDecoded(decoded, asOf)
            (System.nanoTime() - began) / 1_000_000
        }.sorted()
        val millis = timings[RUNS / 2]
        val state = Projection.projectDecoded(decoded, asOf)
        println("NFR-PERF-1: ${decoded.size} ops projected in $millis ms on the JVM (median of $RUNS: $timings)")
        assertEquals(8, state.accounts.size)
        assertTrue("projection took $millis ms", millis < 1_000)
    }

    private companion object {
        const val WARM_UPS = 5
        const val RUNS = 5
    }
}
