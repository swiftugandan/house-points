package dev.housepoints.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.housepoints.app.ui.components.Avatar
import dev.housepoints.app.ui.components.CircleIconButton
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.IconAction
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.Rule
import dev.housepoints.app.ui.components.TextAction
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.ChildId
import dev.housepoints.ledger.DueChore

@Composable
fun HomeScreen(
    model: HomeModel,
    notices: List<String>,
    onDismissNotices: () -> Unit,
    syncLine: String,
    onSettings: () -> Unit,
    onSync: () -> Unit,
    onChild: (ChildId) -> Unit,
    onRecordDue: (ChildId, DueChore) -> Unit,
    onMoreDue: (ChildId) -> Unit,
    onBounties: () -> Unit,
    onRecord: () -> Unit,
    onAddChild: () -> Unit,
) {
    val colors = Hp.colors
    Column(Modifier.fillMaxSize().background(colors.ground)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.l, end = Space.s, top = Space.l),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(model.dateLabel, style = Hp.type.caption, color = colors.inkMuted)
                Text(model.familyName, style = Hp.type.headline.copy(fontWeight = Hp.type.display.fontWeight), color = colors.ink, modifier = Modifier.semantics { heading() })
            }
            IconAction(HpIcons.Settings, "Settings", onSettings)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s, bottom = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HpIcons.Sync, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
            Text(syncLine, style = Hp.type.body, color = colors.inkMuted, modifier = Modifier.weight(1f))
            TextAction("Sync", onSync)
        }
        LazyColumn(Modifier.weight(1f)) {
            item { Rule() }
            items(notices) { notice ->
                dev.housepoints.app.ui.components.NoticeRow(
                    notice,
                    "Entries from the other phone changed a past week's smallest balance, so that week's interest changed too.",
                    action = { TextAction("OK", onDismissNotices) },
                )
            }
            items(model.children, key = { it.id.toString() }) { child ->
                ChildRow(child, onChild, onRecordDue, onMoreDue)
            }
            if (model.children.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().background(colors.surface).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        Text("No children yet", style = Hp.type.title, color = colors.ink)
                        Text("Add each child once. Points, interest and history start from their first entry.", style = Hp.type.body, color = colors.inkMuted)
                        TextAction("Add a child", onAddChild)
                    }
                    Rule()
                }
            }
            if (model.bountyCount > 0) {
                item {
                    Spacer(Modifier.padding(top = Space.xl))
                    Rule()
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).background(colors.surface)
                            .clickable(onClick = onBounties).padding(horizontal = Space.l),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(HpIcons.of(dev.housepoints.contracts.IconKey("car")), contentDescription = null, tint = colors.ink, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(Space.m))
                        val label = if (model.bountyCount == 1) "1 open bounty job" else "${model.bountyCount} open bounty jobs"
                        Text(label, style = Hp.type.body, color = colors.ink, modifier = Modifier.weight(1f))
                        Icon(HpIcons.ChevronRight, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
                    }
                    Rule()
                }
            }
            if (model.paydayLine != null) {
                item {
                    Text(
                        model.paydayLine,
                        style = Hp.type.caption,
                        color = colors.interest,
                        modifier = Modifier.padding(horizontal = Space.l, vertical = Space.l),
                    )
                }
            }
        }
        OutcomeButton(
            label = "Record",
            onClick = onRecord,
            icon = HpIcons.Plus,
            enabled = model.children.isNotEmpty(),
            modifier = Modifier.navigationBarsPadding().padding(Space.l),
        )
    }
}

@Composable
private fun ChildRow(child: HomeChild, onChild: (ChildId) -> Unit, onRecordDue: (ChildId, DueChore) -> Unit, onMoreDue: (ChildId) -> Unit) {
    val colors = Hp.colors
    Column(
        Modifier.fillMaxWidth().background(colors.surface).clickable { onChild(child.id) }.padding(horizontal = Space.l, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(child.name, child.colorIndex)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(child.name, style = Hp.type.title, color = colors.ink)
                Text(child.weekLine, style = Hp.type.caption, color = colors.inkMuted)
            }
            Text(child.balance, style = Hp.type.figureStrong, color = if (child.overdrawn) colors.deduct else colors.ink)
        }
        if (child.due.isNotEmpty()) {
            Row(Modifier.padding(start = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("Due", style = Hp.type.label, color = colors.inkMuted, modifier = Modifier.width(36.dp))
                child.due.forEach { due ->
                    CircleIconButton(HpIcons.of(due.icon), due.description, onClick = { onRecordDue(child.id, due.due) })
                }
                if (child.moreDue > 0) TextAction("${child.moreDue} more", onClick = { onMoreDue(child.id) })
            }
        }
    }
    Rule()
}
