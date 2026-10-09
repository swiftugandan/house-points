package dev.housepoints.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

/** contracts 0.2.0: additive only, so 0.1.0 bodies still decode and 0.2.0 bodies only add keys. */
class AmendmentV2Test {
    private val family = FamilyId(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
    private val device = DeviceId(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"))
    private val child = ChildId(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"))

    private fun op(payload: Payload): Op {
        val (type, body) = OpCodec.encodePayload(payload)
        return Op(OpId(Uuids.v7(1)), family, device, Seq(1), Lamport(1), Op.CURRENT_SCHEMA, type, body)
    }

    @Test
    fun `lock, payout and reward fields round-trip on an adjustment`() {
        val lockId = EntryId(Uuids.v7(10))
        val lock = EntryRecorded(lockId, child, EntryKind.ADJUSTMENT, Points(-500), InstantMs(5), "Locked away for 4 weeks", lock = LockTerms(4, RateBp(200), Points(2000)))
        val payout = EntryRecorded(EntryIds.lockPayout(lockId), child, EntryKind.ADJUSTMENT, Points(541), InstantMs(9), "Locked savings back", lockPayout = lockId)
        val reward = EntryRecorded(EntryId(Uuids.v7(11)), child, EntryKind.ADJUSTMENT, Points(-50), InstantMs(7), "Reward: Screen time", rewardId = RewardId(Uuids.random()))
        listOf(lock, payout, reward).forEach { assertEquals(it, OpCodec.decodePayload(op(it))) }
    }

    @Test
    fun `a 0_1_0 entry body decodes unchanged`() {
        val body = """{"entryId":"${EntryId(Uuids.v7(3))}","childId":"$child","kind":"ADJUSTMENT","points":5,"effectiveAt":1,"note":"x"}"""
        val decoded = OpCodec.decodePayload(Op(OpId(Uuids.v7(2)), family, device, Seq(1), Lamport(1), 1, PayloadType.ENTRY_RECORDED, body)) as EntryRecorded
        assertEquals(null, decoded.lock)
        assertEquals(null, decoded.lockPayout)
        assertEquals(null, decoded.rewardId)
    }

    @Test
    fun `the new payload types round-trip`() {
        val reward = RewardUpsert(RewardId(Uuids.random()), "Screen time, 30 minutes", IconKey("music"), Points(50), archived = false)
        val policy = LockPolicySet(RateBp(100), InstantMs(0))
        assertEquals(reward, OpCodec.decodePayload(op(reward)))
        assertEquals(policy, OpCodec.decodePayload(op(policy)))
        assertEquals(PayloadType.REWARD_UPSERT, OpCodec.encodePayload(reward).first)
        assertEquals(PayloadType.LOCK_POLICY_SET, OpCodec.encodePayload(policy).first)
    }

    @Test
    fun `the payout id is deterministic and distinct from a reversal`() {
        val lockId = EntryId(Uuids.v7(10))
        assertEquals(EntryIds.lockPayout(lockId), EntryIds.lockPayout(lockId))
        assertNotEquals(EntryIds.reversal(lockId), EntryIds.lockPayout(lockId))
    }
}
