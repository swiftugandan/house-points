package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.EntryId
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.LockPolicySet
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.RewardId
import dev.housepoints.contracts.RewardUpsert
import dev.housepoints.contracts.Uuids
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

/**
 * SPEC NFR-DET-4, permanent: a phone still on v0.1.0 ignores every 0.2.0 addition, and must still compute
 * the same balances and weeks as a 0.2.0 phone. Stripping the additions simulates the 0.1.0 reader.
 */
class CrossVersionTest {
    private fun asVersion010(ops: List<Op>): List<Op> = ops.mapNotNull { op ->
        when (val payload = OpCodec.decodePayload(op)) {
            is RewardUpsert, is LockPolicySet -> null
            is EntryRecorded -> {
                val (_, body) = OpCodec.encodePayload(payload.copy(lock = null, lockPayout = null, rewardId = null))
                op.copy(body = body)
            }
            else -> op
        }
    }

    private fun money(state: FamilyState): Map<ChildId, Pair<dev.housepoints.contracts.Micropoints, List<PeriodSummary>>> =
        state.accounts.mapValues { (_, account) -> account.balance to account.periods }

    @Test
    fun `a 0_1_0 phone computes the same balances for logs using every 0_2_0 feature`() {
        repeat(ITERATIONS) { iteration ->
            val seed = SEED + iteration
            val random = Random(seed)
            val log = LogBuilder(random.nextLong()).apply { createFamily(); lockBonus(100) }
            val children = List(1 + random.nextInt(3)) { log.child("C$it", colorIndex = it) }
            val reward = RewardId(Uuids.random())
            log.add(RewardUpsert(reward, "Treat", IconKey("gift"), Points(40), archived = false))
            val start = london("2026-09-01T00:00").value
            val day = 86_400_000L
            val locks = mutableListOf<Pair<EntryId, ChildId>>()
            repeat(15 + random.nextInt(40)) {
                val child = children[random.nextInt(children.size)]
                val at = InstantMs(start + random.nextInt(90) * day + random.nextInt(86_400) * 1000L)
                when (random.nextInt(6)) {
                    0, 1 -> log.entry(child, dev.housepoints.contracts.EntryKind.CHORE, 1L + random.nextInt(300), at)
                    2 -> locks += log.lock(child, 100L + random.nextInt(400), 4 * (1 + random.nextInt(3)), 200, 2000, local(at)) to child
                    3 -> if (locks.isNotEmpty()) {
                        val (lock, owner) = locks[random.nextInt(locks.size)]
                        log.lockReturn(lock, owner, 100, at, EntryId(Uuids.v7(at.value, random.nextInt(), random.nextLong())), note = "broken")
                    }
                    4 -> log.redeem(child, reward, 40, "Treat", local(at))
                    else -> log.entry(child, dev.housepoints.contracts.EntryKind.CASH_OUT, -(1L + random.nextInt(200)), at)
                }
            }
            val asOf = london("2026-12-31T00:00")
            val v020 = Projection.project(log.ops(), asOf)
            Locks.duePayouts(v020).forEach { log.add(it) }
            val withPayouts = log.ops()
            assertEquals("seed $seed", money(Projection.project(withPayouts, asOf)), money(Projection.project(asVersion010(withPayouts), asOf)))
        }
    }

    private fun local(at: InstantMs): String =
        java.time.Instant.ofEpochMilli(at.value).atZone(LONDON).toLocalDateTime().withNano(0).toString()

    private companion object {
        const val ITERATIONS = 150
        const val SEED = 0x0A11_0000L
    }
}
