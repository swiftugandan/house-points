package dev.housepoints.app.widget

import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.home.HomeModels
import dev.housepoints.contracts.ChildId
import java.util.Locale
import java.util.UUID

/** One ruled row of the widget: a child, their disc, balance and Home's week line. */
data class WidgetRow(val id: ChildId, val initial: String, val name: String, val colorIndex: Int, val balance: String, val weekLine: String)

/** What the widget shows (SPEC FR-44). Its title is always the app's name: a phone holds one family, so the family name adds nothing. */
data class WidgetModel(val paydayLine: String?, val rows: List<WidgetRow>)

object WidgetModels {
    /**
     * Built from [HomeModels], so the widget can never disagree with Home. Children stay in the order they
     * were added and are never ranked (DESIGN.md bans comparing siblings).
     */
    fun from(snapshot: FamilySnapshot, locale: Locale): WidgetModel {
        val state = (snapshot as? FamilySnapshot.Ready)?.state
        val family = state?.family ?: return WidgetModel(null, emptyList())
        val formats = Formats(locale, family.zone)
        val home = HomeModels.from(state, formats.localDate(state.asOf), formats, locale)
        return WidgetModel(
            home.paydayLine,
            home.children.map { WidgetRow(it.id, it.name.take(1).uppercase(locale), it.name, it.colorIndex, it.balance, it.weekLine) },
        )
    }
}

/** Where a tap on the widget leads. Carried as an explicit intent's data, so nothing outside the app resolves it. */
sealed interface WidgetLink {
    val uri: String

    data object Record : WidgetLink {
        override val uri: String = "$SCHEME://record"
    }

    data class Account(val child: ChildId) : WidgetLink {
        override val uri: String get() = "$ACCOUNT${child.uuid}"
    }

    companion object {
        private const val SCHEME = "housepoints"
        private const val ACCOUNT = "$SCHEME://account/"

        fun parse(uri: String?): WidgetLink? = when {
            uri == null -> null
            uri == Record.uri -> Record
            uri.startsWith(ACCOUNT) -> uuidOrNull(uri.removePrefix(ACCOUNT))?.let { Account(ChildId(it)) }
            else -> null
        }

        private fun uuidOrNull(text: String): UUID? = try {
            UUID.fromString(text)
        } catch (malformed: IllegalArgumentException) {
            null
        }
    }
}
