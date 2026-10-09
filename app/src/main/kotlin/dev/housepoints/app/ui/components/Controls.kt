package dev.housepoints.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Radius
import dev.housepoints.app.ui.theme.Space

/** The one primary action on a screen; its label says what will happen ("Add 20 to Tom"). */
@Composable
fun OutcomeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val colors = Hp.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(Radius.medium)
            .background(if (enabled) colors.action else colors.sunk)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.l),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (enabled) colors.onAction else colors.inkMuted
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(label, style = Hp.type.title.copy(fontSize = Hp.type.body.fontSize), color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A secondary action: outlined, never competing with the [OutcomeButton]. */
@Composable
fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val colors = Hp.colors
    Row(
        modifier = modifier
            .heightIn(min = Space.touch)
            .clip(Radius.medium)
            .border(BorderStroke(1.dp, colors.rule), Radius.medium)
            .background(colors.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(label, style = Hp.type.label, color = if (enabled) colors.ink else colors.inkMuted)
    }
}

/**
 * A text-only action in the action colour, e.g. "Sync" on the sync line. [inset] false aligns the label with
 * text above it (the touch target stays 48 dp tall).
 */
@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, inset: Boolean = true) {
    Box(
        modifier = modifier
            .heightIn(min = Space.touch)
            .clip(Radius.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (inset) Space.s else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Hp.type.label.copy(fontSize = Hp.type.body.fontSize), color = Hp.colors.action)
    }
}

@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Hp.colors.ink) {
    Box(
        modifier = modifier
            .size(Space.touch)
            .clip(Radius.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** A round 48 dp button holding a picture, used for "record this chore" on Home. */
@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = Space.touch, iconSize: Dp = 24.dp) {
    val colors = Hp.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, colors.rule, CircleShape)
            .background(colors.ground)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(iconSize))
    }
}

/** Header bar: back (or close), title, optional trailing actions. No elevation, no colour fill. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, backIcon: ImageVector = HpIcons.Back, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (onBack == null) Space.l else Space.xs, end = Space.s, top = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconAction(backIcon, if (backIcon == HpIcons.Close) "Close" else "Back", onBack)
        Text(
            title,
            style = Hp.type.headline,
            color = Hp.colors.ink,
            modifier = Modifier.weight(1f).padding(start = Space.xs).semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** A small section label in [Hp.type.label], muted; marks a group, not a heading over a single item. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.xl, bottom = Space.s),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text, style = Hp.type.label, color = Hp.colors.inkMuted, modifier = Modifier.weight(1f).semantics { heading() })
        if (trailing != null) Text(trailing, style = Hp.type.figureStrong, color = Hp.colors.ink)
    }
}

/**
 * DESIGN.md segmented control: a sunk well with the selected segment raised to the surface. With large
 * system text (NFR-A11Y-2) the segments stack into a full-width list rather than clip their labels.
 */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = Hp.colors
    if (LocalDensity.current.fontScale >= LARGE_TEXT_SCALE) {
        Column(
            modifier.fillMaxWidth().clip(Radius.small).background(colors.sunk).padding(Space.xs).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    Modifier.fillMaxWidth().heightIn(min = Space.touch).clip(Radius.small)
                        .background(if (isSelected) colors.surface else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) })
                        .padding(horizontal = Space.m),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(label, style = Hp.type.label, color = colors.ink) }
            }
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.small)
            .background(colors.sunk)
            .padding(Space.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Space.touch)
                    .clip(Radius.small)
                    .background(if (isSelected) colors.surface else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Hp.type.label, color = colors.ink, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** − value + with 48 dp buttons; the value in [Hp.type.figureLarge]. */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Hp.type.label, color = Hp.colors.inkMuted, modifier = Modifier.weight(1f))
        CircleIconButton(HpIcons.Minus, "Less", onMinus)
        Text(value, style = Hp.type.figureLarge, color = Hp.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 72.dp))
        CircleIconButton(HpIcons.Plus, "More", onPlus)
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(Radius.small)
            .background(Hp.colors.sunk)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).background(color))
    }
}

/** Plain-text notice row for flags (DESIGN.md "Flags"): never a toast or a dialog. */
@Composable
fun NoticeRow(title: String, body: String, modifier: Modifier = Modifier, titleColor: Color = Hp.colors.ink, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().background(Hp.colors.surface).padding(horizontal = Space.l, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(title, style = Hp.type.title.copy(fontSize = Hp.type.body.fontSize), color = titleColor)
        Text(body, style = Hp.type.caption, color = Hp.colors.inkMuted)
        if (action != null) action()
    }
    Rule()
}

@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hp.colors.rule))
}

/** A selectable picture tile, used for values, icons and children in pickers. */
@Composable
fun PickTile(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Hp.colors
    Column(
        modifier = modifier
            .heightIn(min = Space.touch)
            .semantics { contentDescription = label.ifEmpty { icon.name } }
            .clip(Radius.medium)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.action else colors.rule, Radius.medium)
            .background(colors.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Space.s, horizontal = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(24.dp))
        if (label.isNotEmpty()) {
            Text(label, style = if (selected) Hp.type.label else Hp.type.caption, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Above this system font scale, horizontal segments no longer fit their labels on a phone. */
private const val LARGE_TEXT_SCALE = 1.5f
