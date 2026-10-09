package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.InstantMs
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

/**
 * SPEC NFR-DET-1: the state is a function of the op *set*. Order of arrival and repeated delivery (a sync
 * that ran twice) must not change a single micropoint.
 */
class DeterminismPropertyTest {
    private val iterations = 200

    @Test
    fun `any permutation or duplication of the log gives the same state`() {
        repeat(iterations) { iteration ->
            val seed = 0x5EED0000L + iteration
            val random = Random(seed)
            val log = randomLog(random)
            val asOf = london("2026-12-01T00:00")
            val expected = Projection.project(log, asOf)

            val shuffled = log.shuffled(random)
            val duplicated = (log + log.filter { random.nextInt(3) == 0 }).shuffled(random)
            assertEquals("seed $seed (shuffled)", expected, Projection.project(shuffled, asOf))
            assertEquals("seed $seed (duplicated)", expected, Projection.project(duplicated, asOf))
        }
    }

    private fun randomLog(random: Random): List<dev.housepoints.contracts.Op> {
        val log = LogBuilder(random.nextLong()).apply { createFamily() }
        val children: List<ChildId> = List(1 + random.nextInt(3)) { log.child("Child $it", colorIndex = it) }
        val start = london("2026-09-01T00:00").value
        val span = london("2026-11-30T00:00").value - start
        val entries = mutableListOf<Pair<dev.housepoints.contracts.EntryId, Pair<ChildId, Long>>>()
        repeat(10 + random.nextInt(60)) {
            val child = children[random.nextInt(children.size)]
            val at = InstantMs(start + (random.nextDouble() * span).toLong() / 60_000 * 60_000)
            val device = if (random.nextBoolean()) log.phoneA else log.phoneB
            val lamport = if (random.nextInt(4) == 0) (1L + random.nextInt(200)) else null
            when (random.nextInt(6)) {
                0, 1 -> {
                    val points = 1L + random.nextInt(300)
                    entries += log.entry(child, EntryKind.CHORE, points, at, device, lamport = lamport) to (child to points)
                }
                2 -> {
                    val points = 1L + random.nextInt(200)
                    entries += log.entry(child, EntryKind.AWARD, points, at, device, lamport = lamport, note = "n") to (child to points)
                }
                3 -> log.entry(child, EntryKind.CASH_OUT, -(1L + random.nextInt(250)), at, device, lamport = lamport)
                4 -> if (entries.isNotEmpty()) {
                    val (target, owner) = entries[random.nextInt(entries.size)]
                    log.reverse(target, owner.first, owner.second, at, device, lamport = lamport)
                }
                else -> log.interestRate(random.nextInt(300), cap = 500L + random.nextInt(3000), from = "2026-10-${10 + random.nextInt(15)}T00:00", device = device, lamport = lamport)
            }
        }
        return log.ops()
    }
}
