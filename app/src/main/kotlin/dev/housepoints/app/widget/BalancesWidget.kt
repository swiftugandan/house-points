package dev.housepoints.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.housepoints.app.HousePointsApp
import dev.housepoints.app.MainActivity
import dev.housepoints.app.R
import java.util.Locale

/**
 * SPEC FR-44: the family's passbook page on the home screen (DESIGN.md "Passbook"): ruled rows, a disc in each
 * child's colour, figures in a right-hand column, the payday line in brass and one action, Record.
 *
 * The content collects the repository's state itself, so a Glance session that is still alive when the log
 * changes recomposes with the new balances; `updateAll` from [dev.housepoints.app.AppGraph] restarts sessions
 * that have ended. Shapes are drawables because Glance's cornerRadius is ignored below API 31; colours come
 * from [WidgetPalette]. Widgets can't
 * load the app's bundled fonts, so type is the system face, weighted to echo the app's scale.
 */
class BalancesWidget : GlanceAppWidget() {
    /** Exact, so the layout knows its real height and shows each child's week line when there is room. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = (context.applicationContext as HousePointsApp).graph
        val repository = graph.repository
        repository.refreshNow()
        val locale = Locale.getDefault()
        provideContent {
            val snapshot by repository.snapshot.collectAsState()
            val night by graph.night.collectAsState()
            val model = WidgetModels.from(snapshot, locale)
            CompositionLocalProvider(LocalWidgetPalette provides WidgetPalette.of(night)) {
                if (LocalSize.current.height < PAGE.height) Strip(model) else Page(model)
            }
        }
    }

    @Composable
    private fun Page(model: WidgetModel) {
        val size = LocalSize.current
        val p = LocalWidgetPalette.current
        val roomForLines = model.rows.size * ROW_WITH_LINE.value <= (size.height - HEADER - PADDING * 2).value
        Column(GlanceModifier.fillMaxSize().background(ImageProvider(p.page)).padding(PADDING)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(string(R.string.app_name), maxLines = 1, style = TextStyle(color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    model.paydayLine?.let { Text(it, maxLines = 1, style = TextStyle(color = p.interest, fontSize = 12.sp)) }
                }
                if (model.rows.isNotEmpty()) {
                    Spacer(GlanceModifier.width(8.dp))
                    RecordButton()
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            Rule()
            if (model.rows.isEmpty()) {
                Text(
                    "Open House Points to set up your family.",
                    modifier = GlanceModifier.padding(top = 12.dp).clickable(open(null)),
                    style = TextStyle(color = p.inkMuted, fontSize = 14.sp),
                )
            }
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(model.rows, itemId = { it.id.uuid.mostSignificantBits xor it.id.uuid.leastSignificantBits }) { row ->
                    Column(GlanceModifier.fillMaxWidth()) {
                        ChildRow(row, roomForLines)
                        Rule()
                    }
                }
            }
        }
    }

    @Composable
    private fun ChildRow(row: WidgetRow, withLine: Boolean) {
        val p = LocalWidgetPalette.current
        Row(
            GlanceModifier.fillMaxWidth().height(if (withLine) ROW_WITH_LINE else ROW).clickable(open(WidgetLink.Account(row.id))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Disc(row, DISC)
            Spacer(GlanceModifier.width(12.dp))
            // Like a passbook entry: name and figure on one line, the note running beneath both.
            Column(GlanceModifier.defaultWeight()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name, maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                    Text(row.balance, maxLines = 1, style = figure(p, 22))
                }
                if (withLine) Text(row.weekLine, maxLines = 1, style = TextStyle(color = p.inkMuted, fontSize = 12.sp))
            }
        }
    }

    /** One line for a short widget: each child's disc and balance, then Record. */
    @Composable
    private fun Strip(model: WidgetModel) {
        val p = LocalWidgetPalette.current
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(p.page)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (model.rows.isEmpty()) {
                Text(string(R.string.app_name), modifier = GlanceModifier.defaultWeight().clickable(open(null)), style = TextStyle(color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            } else {
                Row(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    model.rows.forEach { row ->
                        Row(GlanceModifier.padding(end = 14.dp).clickable(open(WidgetLink.Account(row.id))), verticalAlignment = Alignment.CenterVertically) {
                            Disc(row, SMALL_DISC)
                            Spacer(GlanceModifier.width(6.dp))
                            Text(row.balance, maxLines = 1, style = figure(p, 17))
                        }
                    }
                }
                RecordButton()
            }
        }
    }

    @Composable
    private fun Disc(row: WidgetRow, size: Dp) {
        Box(GlanceModifier.size(size).background(ImageProvider(discFor(row.colorIndex))), contentAlignment = Alignment.Center) {
            Text(
                row.initial,
                style = TextStyle(color = ON_DISC, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
        }
    }

    @Composable
    private fun RecordButton() {
        val p = LocalWidgetPalette.current
        Box(
            GlanceModifier.size(RECORD).background(ImageProvider(p.record)).clickable(open(WidgetLink.Record)),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.widget_plus), contentDescription = "Record", modifier = GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(p.onRecord))
        }
    }

    @Composable
    private fun string(id: Int): String = LocalContext.current.getString(id)

    @Composable
    private fun Rule() {
        Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(LocalWidgetPalette.current.rule))
    }

    @Composable
    private fun open(link: WidgetLink?): Action {
        val intent = Intent(LocalContext.current, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        link?.let { intent.data = Uri.parse(it.uri) }
        return actionStartActivity(intent)
    }

    private fun figure(p: WidgetPalette, size: Int) =
        TextStyle(color = p.ink, fontSize = size.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily("sans-serif-condensed"))

    @DrawableRes
    private fun discFor(colorIndex: Int): Int = DISCS[Math.floorMod(colorIndex, DISCS.size)]

    private companion object {
        val PAGE = DpSize(180.dp, 110.dp)
        val PADDING = 16.dp
        val HEADER = 52.dp
        val ROW = 44.dp
        val ROW_WITH_LINE = 56.dp
        val DISC = 34.dp
        val SMALL_DISC = 26.dp
        val RECORD = 36.dp

        val ON_DISC = ColorProvider(Color.White)

        /** Tokens.kt children fills, in order. */
        val DISCS = listOf(
            R.drawable.widget_disc_0, R.drawable.widget_disc_1, R.drawable.widget_disc_2,
            R.drawable.widget_disc_3, R.drawable.widget_disc_4, R.drawable.widget_disc_5,
        )
    }
}

class BalancesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalancesWidget()
}
