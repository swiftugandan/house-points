package dev.housepoints.app.child

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.housepoints.app.ui.components.Avatar
import dev.housepoints.app.ui.components.Balance
import dev.housepoints.app.ui.components.CircleIconButton
import dev.housepoints.app.ui.components.DoneBadge
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.IconAction
import dev.housepoints.app.ui.components.Jar
import dev.housepoints.app.ui.components.LedgerRow
import dev.housepoints.app.ui.components.ProgressBar
import dev.housepoints.app.ui.components.Rule
import dev.housepoints.app.ui.components.SectionLabel
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.Points

@Composable
private fun ChildHeader(model: ChildViewModel, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.l, end = Space.s, top = Space.l), verticalAlignment = Alignment.CenterVertically) {
        Avatar(model.name, model.colorIndex)
        Spacer(Modifier.width(Space.m))
        Text(model.name, style = Hp.type.headline.copy(fontWeight = Hp.type.display.fontWeight), color = Hp.colors.ink, modifier = Modifier.weight(1f))
        IconAction(HpIcons.Lock, "Give the phone back to a parent", onExit, tint = Hp.colors.inkMuted)
    }
}

/** DESIGN.md "Child view: Picture style": the jar, one sentence read aloud on tap, today's jobs. No other text. */
@Composable
fun PictureView(model: ChildViewModel, animate: Boolean, onSpeak: (String) -> Unit, onExit: () -> Unit) {
    val colour = Hp.colors.child(model.colorIndex)
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        ChildHeader(model, onExit)
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.s), contentAlignment = Alignment.Center) {
            Jar(
                model.jar, colour, model.goalIcon?.let(HpIcons::of),
                description = model.sentence + (model.goalTitle?.let { " The line is ${it}." } ?: ""),
                animate = animate,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
            Text(model.sentence, style = Hp.type.pictureLabel, color = Hp.colors.ink, modifier = Modifier.weight(1f))
            CircleIconButton(HpIcons.Speaker, "Read it to me", onClick = { onSpeak(model.sentence) }, size = 56.dp, iconSize = 26.dp)
        }
        Spacer(Modifier.size(Space.l))
        if (model.today.isNotEmpty()) {
            Rule()
            LazyRow(
                Modifier.fillMaxWidth().background(Hp.colors.surface).navigationBarsPadding(),
                contentPadding = PaddingValues(Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                items(model.today) { job ->
                    Box(Modifier.size(72.dp).semantics { contentDescription = job.title + if (job.done) ": done" else ": not yet" }) {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape)
                                .background(if (job.done) Hp.colors.ground else Hp.colors.surface)
                                .border(if (job.done) 1.dp else 2.dp, if (job.done) Hp.colors.rule else Hp.colors.inkMuted, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(HpIcons.of(job.icon), contentDescription = null, tint = Hp.colors.ink, modifier = Modifier.size(40.dp))
                        }
                        if (job.done) DoneBadge(Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** DESIGN.md "Child view: Number style". The slider only ever shows interest, never imagined earnings. */
@Composable
fun NumberView(model: ChildViewModel, afterWeeks: (Int) -> Points, formatPoints: (Points) -> String, onExit: () -> Unit) {
    var weeks by rememberSaveable { mutableFloatStateOf(DEFAULT_WEEKS) }
    val total = afterWeeks(weeks.toInt())
    val gained = Points(total.value - model.balance.value.coerceAtLeast(0))
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        ChildHeader(model, onExit)
        LazyColumn(Modifier.weight(1f)) {
            item { Balance(model.balance, model.balanceText, model.worth, Modifier.padding(Space.l)) }
            if (model.goalTitle != null) {
                item {
                    Rule()
                    Column(Modifier.fillMaxWidth().background(Hp.colors.surface).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            model.goalIcon?.let { Icon(HpIcons.of(it), contentDescription = null, tint = Hp.colors.child(model.colorIndex).accent, modifier = Modifier.size(28.dp)) }
                            Spacer(Modifier.width(Space.m))
                            Text(model.goalTitle, style = Hp.type.title, color = Hp.colors.ink, modifier = Modifier.weight(1f))
                            Text(model.goalProgress.orEmpty(), style = Hp.type.figure, color = Hp.colors.inkMuted)
                        }
                        ProgressBar(model.goalFraction, Hp.colors.child(model.colorIndex).fill, "Goal progress ${model.goalProgress}")
                        Row {
                            Text("If you keep earning like the last 4 weeks", style = Hp.type.body, color = Hp.colors.ink, modifier = Modifier.weight(1f))
                            Text(model.atPace.orEmpty(), style = Hp.type.label.copy(fontSize = Hp.type.body.fontSize), color = Hp.colors.ink)
                        }
                        Row {
                            Text("With interest only", style = Hp.type.body, color = Hp.colors.inkMuted, modifier = Modifier.weight(1f))
                            Text(model.interestOnly.orEmpty(), style = Hp.type.body, color = Hp.colors.inkMuted)
                        }
                    }
                    Rule()
                }
            }
            item {
                Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("What if I wait?", style = Hp.type.title, color = Hp.colors.ink, modifier = Modifier.weight(1f))
                        Text(if (weeks.toInt() == 1) "1 week" else "${weeks.toInt()} weeks", style = Hp.type.figure, color = Hp.colors.ink)
                    }
                    Slider(
                        value = weeks, onValueChange = { weeks = it }, valueRange = 1f..MAX_WEEKS, steps = (MAX_WEEKS - 2).toInt(),
                        colors = SliderDefaults.colors(thumbColor = Hp.colors.child(model.colorIndex).fill, activeTrackColor = Hp.colors.child(model.colorIndex).fill, inactiveTrackColor = Hp.colors.sunk, activeTickColor = Hp.colors.child(model.colorIndex).fill, inactiveTickColor = Hp.colors.sunk),
                        modifier = Modifier.semantics { contentDescription = "Weeks to wait" },
                    )
                    Rule()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("You'd have", style = Hp.type.body, color = Hp.colors.ink, modifier = Modifier.weight(1f))
                        Text(formatPoints(total), style = Hp.type.figureLarge, color = Hp.colors.ink)
                    }
                    Row {
                        Text("Of which interest", style = Hp.type.body, color = Hp.colors.interest, modifier = Modifier.weight(1f))
                        Text("+" + formatPoints(gained), style = Hp.type.figureStrong, color = Hp.colors.interest)
                    }
                    Text("This counts interest only, not new jobs. ${model.interestExplanation}", style = Hp.type.caption, color = Hp.colors.inkMuted)
                }
            }
            if (model.recent.isNotEmpty()) {
                item { SectionLabel("Lately") }
                item { Rule() }
                items(model.recent) { LedgerRow(it) }
            }
            item { Spacer(Modifier.navigationBarsPadding().size(Space.xl)) }
        }
    }
}

private const val DEFAULT_WEEKS = 26f
private const val MAX_WEEKS = 52f
