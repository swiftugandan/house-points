package dev.housepoints.app

import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.app.widget.WidgetModel
import dev.housepoints.app.widget.WidgetModels
import dev.housepoints.app.widget.WidgetRow
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.OpId
import dev.housepoints.contracts.Payload
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Seq
import dev.housepoints.contracts.Uuids
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Projection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** SPEC FR-44: the widget lists children in the order they were added, never ranked by balance. */
class WidgetModelTest {
    private val zone = ZoneId.of("Europe/London")
    private val payloads = mutableListOf<Payload>()
    private fun at(local: String) = InstantMs(LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli())

    private fun state(): FamilyState = Projection.project(
        payloads.mapIndexed { i, p ->
            val (type, body) = OpCodec.encodePayload(p)
            Op(OpId(Uuids.v7(i + 1L)), FAMILY, PHONE, Seq(i + 1L), Lamport(i + 1L), Op.CURRENT_SCHEMA, type, body)
        },
        at("2026-10-09T12:00"),
    )

    private fun record(action: Action) {
        payloads += (action as Action.Record).payloads
    }

    @Test
    fun `children appear in creation order with their displayed balance, not sorted by balance`() {
        payloads += FamilyActions.createFamily("The Okellos", CurrencyCode("GBP"), zone, DayOfWeek.MONDAY, PHONE, "Phone")
        listOf("Zed", "Ada").forEach { record(FamilyActions.addChild(state(), it, DisplayStyle.NUMBER)) }
        val ada = state().children.single { it.name == "Ada" }.id
        record(FamilyActions.award(setOf(ada), null, Points(1250), "Saved up", at("2026-10-09T09:00")))
        val model = WidgetModels.from(FamilySnapshot.Ready(FAMILY, state()), Locale.UK)
        assertEquals(WidgetModel("The Okellos", listOf(WidgetRow("Zed", 0, "0"), WidgetRow("Ada", 1, "1,250"))), model)
    }

    @Test
    fun `before a family exists the widget says how to start`() {
        assertEquals(WidgetModel("House Points", emptyList()), WidgetModels.from(FamilySnapshot.NoFamily, Locale.UK))
    }

    private companion object {
        val FAMILY = FamilyId(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000005"))
        val PHONE = DeviceId(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000005"))
    }
}
