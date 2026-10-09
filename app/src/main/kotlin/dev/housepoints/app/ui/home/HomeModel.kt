package dev.housepoints.app.ui.home

import dev.housepoints.app.ui.format.Formats
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.Micropoints
import dev.housepoints.contracts.Points
import dev.housepoints.ledger.Chores
import dev.housepoints.ledger.DueChore
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Flag
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class HomeModel(
    val dateLabel: String,
    val familyName: String,
    val children: List<HomeChild>,
    val bountyCount: Int,
    val paydayLine: String?,
)

data class HomeChild(
    val id: ChildId,
    val name: String,
    val colorIndex: Int,
    val balance: String,
    val weekLine: String,
    val overdrawn: Boolean,
    val due: List<HomeDue>,
    val moreDue: Int,
)

data class HomeDue(val due: DueChore, val icon: IconKey, val description: String)

object HomeModels {
    private const val DUE_SHOWN = 3

    fun from(state: FamilyState, today: LocalDate, formats: Formats, locale: Locale): HomeModel {
        val family = requireNotNull(state.family)
        val overdrawn = state.flags.filterIsInstance<Flag.Overdrawn>().map { it.child }.toSet()
        val children = state.children.filter { !it.archived }.map { child ->
            val account = state.account(child.id)
            val earned = account?.current?.credits ?: Points.ZERO
            val due = Chores.dueFor(state, child.id, today)
            HomeChild(
                id = child.id,
                name = child.name,
                colorIndex = child.colorIndex,
                balance = formats.points(account?.displayed ?: Points.ZERO),
                weekLine = if (earned > Points.ZERO) "${formats.signed(earned)} this week" else "Nothing yet this week",
                overdrawn = child.id in overdrawn,
                due = due.take(DUE_SHOWN).map {
                    HomeDue(it, it.chore.icon, "Record ${it.chore.title} for ${child.name}, ${formats.points(it.chore.points)} points")
                },
                moreDue = (due.size - DUE_SHOWN).coerceAtLeast(0),
            )
        }
        return HomeModel(
            dateLabel = formats.longDay(today),
            familyName = family.name,
            children = children,
            bountyCount = Chores.bounties(state).size,
            paydayLine = paydayLine(state, locale),
        )
    }

    private fun paydayLine(state: FamilyState, locale: Locale): String? {
        val currents = state.accounts.values.mapNotNull { it.current }
        val payday = currents.firstOrNull()?.end ?: return null
        val dayName = java.time.Instant.ofEpochMilli(payday.value).atZone(requireNotNull(state.family).zone)
            .dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val expected = currents.fold(Micropoints.ZERO) { sum, c -> sum + c.expectedInterest }.floorPoints()
        return if (expected > Points.ZERO) {
            "Payday is $dayName. About ${expected.value} ${if (expected.value == 1L) "point" else "points"} of interest are due."
        } else {
            "Payday is $dayName."
        }
    }
}
