package dev.housepoints.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.housepoints.app.HousePointsApp
import dev.housepoints.app.MainActivity
import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.theme.DarkColors
import dev.housepoints.app.ui.theme.LightColors
import dev.housepoints.contracts.Points
import java.util.Locale

/** One line of the widget: a child and their displayed balance. */
data class WidgetRow(val name: String, val colorIndex: Int, val balance: String)

/** What the widget shows, built from the family state (SPEC FR-44). */
data class WidgetModel(val title: String, val rows: List<WidgetRow>)

/**
 * SPEC FR-44: each child's balance at a glance, in the order children were added and never ranked
 * (DESIGN.md bans comparing siblings). Widgets can't load the app's bundled fonts, so this uses system type
 * with the same colour tokens.
 */
class BalancesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = (context.applicationContext as HousePointsApp).graph
        val model = when (val snapshot = graph.repository.refreshNow()) {
            is FamilySnapshot.Ready -> {
                val state = snapshot.state
                val family = state.family
                val formats = Formats(Locale.getDefault(), family?.zone ?: java.time.ZoneId.systemDefault())
                WidgetModel(
                    family?.name ?: "House Points",
                    state.children.filter { !it.archived }.map { child ->
                        WidgetRow(child.name, child.colorIndex, formats.points(state.account(child.id)?.displayed ?: Points.ZERO))
                    },
                )
            }
            else -> WidgetModel("House Points", emptyList())
        }
        provideContent { Balances(model) }
    }

    @Composable
    private fun Balances(model: WidgetModel) {
        Column(
            GlanceModifier.fillMaxSize().background(color(LightColors.surface, DarkColors.surface)).cornerRadius(CORNER)
                .padding(PADDING).clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(model.title, style = TextStyle(color = color(LightColors.ink, DarkColors.ink), fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(8.dp))
            if (model.rows.isEmpty()) {
                Text("Open House Points to set up your family.", style = TextStyle(color = color(LightColors.inkMuted, DarkColors.inkMuted), fontSize = 14.sp))
            }
            model.rows.forEach { row ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val tone = LightColors.child(row.colorIndex)
                    Box(GlanceModifier.size(DOT).cornerRadius(DOT_RADIUS).background(color(tone.fill, DarkColors.child(row.colorIndex).fill))) {}
                    Spacer(GlanceModifier.width(8.dp))
                    Text(row.name, style = TextStyle(color = color(LightColors.ink, DarkColors.ink), fontSize = 15.sp), modifier = GlanceModifier.defaultWeight())
                    Text(
                        row.balance,
                        style = TextStyle(color = color(LightColors.ink, DarkColors.ink), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }

    private fun color(day: Color, night: Color) = ColorProvider(day = day, night = night)

    private companion object {
        val CORNER = 12.dp
        val PADDING = 12.dp
        val DOT = 10.dp
        val DOT_RADIUS = 5.dp
    }
}

class BalancesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalancesWidget()
}
