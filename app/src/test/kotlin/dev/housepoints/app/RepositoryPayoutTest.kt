package dev.housepoints.app

import dev.housepoints.app.family.Clock
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.family.FamilyRepository
import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.app.family.RecordResult
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.Points
import dev.housepoints.app.family.Action
import dev.housepoints.sync.InMemoryOpLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** SPEC FR-50: the app records a matured payout exactly once, and the loop settles. */
@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryPayoutTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(local: String) = InstantMs(LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli())

    @Test
    fun `a matured lock is paid out once and then nothing more is recorded`() = runTest {
        var now = at("2026-10-14T12:00")
        val clock = Clock { now }
        val log = InMemoryOpLog()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FamilyRepository(log, DeviceId(UUID.randomUUID()), clock, dispatcher)
        repository.createFamily(
            FamilyId(UUID.randomUUID()),
            FamilyActions.createFamily("T", CurrencyCode("GBP"), zone, DayOfWeek.MONDAY, repository.self, "Phone"),
        )
        suspend fun state() = (repository.refreshNow() as FamilySnapshot.Ready).state
        suspend fun record(action: Action) = assertEquals(true, repository.record((action as Action.Record).payloads) is RecordResult.Recorded)

        record(FamilyActions.addChild(state(), "Ada", DisplayStyle.NUMBER))
        val ada = state().children.single().id
        record(FamilyActions.award(setOf(ada), null, Points(1000), "Birthday", at("2026-10-13T09:00")))
        record(FamilyActions.lock(state(), ada, Points(500), 4, now))

        suspend fun payouts() = log.all().map { OpCodec.decodePayload(it) }.filterIsInstance<EntryRecorded>().filter { it.lockPayout != null }

        repository.settle()
        assertEquals("not due before maturity", 0, payouts().size)

        now = at("2026-11-16T09:00")
        repository.settle()
        assertEquals(1, payouts().size)
        val opsAfterPayout = log.all().size

        repository.settle()
        repository.settle()
        assertEquals("settles: nothing more recorded", opsAfterPayout, log.all().size)
        assertEquals(Points(541), payouts().single().points)
    }
}
