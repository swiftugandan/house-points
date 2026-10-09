package dev.housepoints.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.Points

/** A child's identity disc with their initial (DESIGN.md "Child row"). Decorative: the name is always shown next to it. */
@Composable
fun Avatar(name: String, colorIndex: Int, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val colour = Hp.colors.child(colorIndex)
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(colour.fill).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = Hp.type.initials.copy(fontSize = (size.value * 0.45f).sp, lineHeight = (size.value * 0.5f).sp),
            color = colour.onFill,
        )
    }
}

/** Ledger row texts, already formatted by the screen. */
data class LedgerRowModel(
    val date: String,
    val title: String,
    val note: String?,
    val amount: String,
    val amountSub: String?,
    val kind: LedgerRowKind,
    val reversed: Boolean,
)

enum class LedgerRowKind { NORMAL, DEDUCTION, INTEREST, REVERSAL }

/** DESIGN.md ledger row: date | title + note | signed amount, ruled underneath. */
@Composable
fun LedgerRow(model: LedgerRowModel, onClick: (() -> Unit)? = null) {
    val colors = Hp.colors
    val type = Hp.type
    val accent = when (model.kind) {
        LedgerRowKind.INTEREST -> colors.interest
        LedgerRowKind.DEDUCTION -> colors.deduct
        else -> colors.ink
    }
    val strike = if (model.reversed || model.kind == LedgerRowKind.REVERSAL) TextDecoration.LineThrough else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.l, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Text(model.date, style = type.figure, color = if (model.kind == LedgerRowKind.INTEREST) accent else colors.inkMuted, modifier = Modifier.width(72.dp))
        Column(Modifier.weight(1f)) {
            Text(model.title, style = type.title.copy(fontSize = type.body.fontSize, textDecoration = strike), color = accent)
            if (!model.note.isNullOrBlank()) {
                Text(model.note, style = type.caption, color = if (model.kind == LedgerRowKind.DEDUCTION) colors.deduct else colors.inkMuted)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                model.amount,
                style = (if (model.kind == LedgerRowKind.INTEREST) type.figureStrong else type.figure).copy(textDecoration = strike),
                color = accent,
                textAlign = TextAlign.End,
            )
            if (model.amountSub != null) Text(model.amountSub, style = type.caption, color = colors.inkMuted)
        }
    }
    Rule()
}

/** The weekly payday line: a brass double rule above, "Payday · 1% of 200", amount (DESIGN.md "Payday line"). */
@Composable
fun PaydayRow(date: String, title: String, detail: String?, amount: String, onClick: (() -> Unit)? = null) {
    val colors = Hp.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        DoubleRule(colors.interest)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = Space.l, vertical = 10.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text(date, style = Hp.type.figure, color = colors.interest, modifier = Modifier.width(72.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Hp.type.title.copy(fontSize = Hp.type.body.fontSize), color = colors.interest)
                if (detail != null) Text(detail, style = Hp.type.caption, color = colors.interest)
            }
            Text(amount, style = Hp.type.figureStrong, color = colors.interest)
        }
        Rule()
    }
}

@Composable
fun DoubleRule(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        drawLine(color, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx())
        drawLine(color, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx())
    }
}

/** Week header above a group of ledger rows: dates on the left, the week's closing balance on the right. */
@Composable
fun WeekHeader(label: String, closing: String?) {
    SectionLabel(label, trailing = closing)
}

/** The big balance with its unit and money value (DESIGN.md "display"). */
@Composable
fun Balance(points: Points, pointsText: String, worth: String?, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$pointsText points" + (worth?.let { ", worth $it" } ?: "") }) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(pointsText, style = Hp.type.display, color = if (points.value < 0) Hp.colors.deduct else Hp.colors.ink)
            Text("points", style = Hp.type.title.copy(fontWeight = Hp.type.body.fontWeight), color = Hp.colors.inkMuted, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (worth != null) Text("Worth $worth", style = Hp.type.body, color = Hp.colors.inkMuted)
    }
}

/** A circle with a tick in the action colour, laid over a picture to say "done". */
@Composable
fun DoneBadge(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Hp.colors.action).border(2.dp, Hp.colors.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(HpIcons.Check, contentDescription = null, tint = Hp.colors.onAction, modifier = Modifier.size(size * 0.58f))
    }
}
