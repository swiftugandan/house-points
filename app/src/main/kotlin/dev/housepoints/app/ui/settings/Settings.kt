package dev.housepoints.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.ui.components.Avatar
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.PickTile
import dev.housepoints.app.ui.components.QuietButton
import dev.housepoints.app.ui.components.Rule
import dev.housepoints.app.ui.components.Segmented
import dev.housepoints.app.ui.components.Stepper
import dev.housepoints.app.ui.components.TextAction
import dev.housepoints.app.ui.components.TopBar
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.onboarding.Field
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Radius
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.Points
import dev.housepoints.ledger.ChildRecord
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.RewardRecord
import dev.housepoints.ledger.ValueRecord

enum class SettingsPage(val title: String, val detail: String, val icon: () -> ImageVector) {
    CHILDREN("Children", "Names, colours and how each one sees their account", { HpIcons.of(IconKey("people")) }),
    JOBS("Jobs", "Paid, unpaid and bounty jobs", { HpIcons.of(IconKey("bin")) }),
    VALUES("Family values", "What behaviour awards are for", { HpIcons.of(IconKey("heart")) }),
    REWARDS("Rewards shop", "Treats children can spend points on", { HpIcons.of(IconKey("gift")) }),
    MONEY("Money rules", "Interest, exchange rate, taking points away", { HpIcons.of(IconKey("star")) }),
    PHONES("Phones", "Pair a phone, remove a lost one", { HpIcons.Phone }),
    BACKUP("Back up", "Save or restore an encrypted copy", { HpIcons.Share }),
    DIAGNOSTICS("Diagnostics", "Sync log and counts, for when something looks wrong", { HpIcons.Settings }),
}

@Composable
fun SettingsScreen(familyName: String, version: String, onBack: () -> Unit, onOpen: (SettingsPage) -> Unit, onCheckForUpdate: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        TopBar("Settings", onBack)
        Text(familyName, style = Hp.type.body, color = Hp.colors.inkMuted, modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Rule()
            SettingsPage.entries.forEach { page ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Hp.colors.surface).clickable { onOpen(page) }.padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(page.icon(), contentDescription = null, tint = Hp.colors.ink, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(page.title, style = Hp.type.title.copy(fontSize = Hp.type.body.fontSize), color = Hp.colors.ink)
                        Text(page.detail, style = Hp.type.caption, color = Hp.colors.inkMuted)
                    }
                    Icon(HpIcons.ChevronRight, contentDescription = null, tint = Hp.colors.inkMuted, modifier = Modifier.size(20.dp))
                }
                Rule()
            }
            Column(Modifier.navigationBarsPadding().padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Version $version", style = Hp.type.label, color = Hp.colors.ink)
                TextAction("Check for a newer version", onCheckForUpdate, inset = false)
                Text(
                    "Opens the releases page in your browser. House Points itself never goes online. " +
                        "Free software under the MIT licence; the Bricolage Grotesque and Atkinson Hyperlegible typefaces are under the SIL Open Font License.",
                    style = Hp.type.caption, color = Hp.colors.inkMuted,
                )
            }
        }
    }
}

/** List of children; tap one to rename, change their view or archive them (SPEC FR-5). */
@Composable
fun ChildrenSettings(state: FamilyState, onBack: () -> Unit, onAdd: () -> Unit, perform: (Action) -> Unit) {
    var editing by remember { mutableStateOf<ChildRecord?>(null) }
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        TopBar("Children", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.size(Space.l))
            Rule()
            state.children.forEach { child ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Hp.colors.surface).clickable { editing = child }.padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(child.name, child.colorIndex, size = 40.dp)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(child.name, style = Hp.type.title.copy(fontSize = Hp.type.body.fontSize), color = if (child.archived) Hp.colors.inkMuted else Hp.colors.ink)
                        Text(
                            (if (child.displayStyle == DisplayStyle.PICTURE) "Pictures" else "Numbers") + if (child.archived) " · archived" else "",
                            style = Hp.type.caption, color = Hp.colors.inkMuted,
                        )
                    }
                    Icon(HpIcons.Edit, contentDescription = null, tint = Hp.colors.inkMuted, modifier = Modifier.size(20.dp))
                }
                Rule()
            }
        }
        OutcomeButton("Add a child", onAdd, icon = HpIcons.Plus, modifier = Modifier.navigationBarsPadding().padding(Space.l))
    }
    editing?.let { child -> ChildEditSheet(child, onDismiss = { editing = null }, perform = perform) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildEditSheet(child: ChildRecord, onDismiss: () -> Unit, perform: (Action) -> Unit) {
    var name by rememberSaveable { mutableStateOf(child.name) }
    var style by rememberSaveable { mutableStateOf(child.displayStyle) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Hp.colors.surface, shape = Radius.sheet) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Text("Edit ${child.name}", style = Hp.type.headline, color = Hp.colors.ink)
            Field("Name", name, { name = it })
            Segmented(listOf(DisplayStyle.PICTURE to "Pictures", DisplayStyle.NUMBER to "Numbers"), style, { style = it })
            OutcomeButton("Save", enabled = name.isNotBlank(), onClick = {
                perform(FamilyActions.editChild(child.id, name = name, style = style))
                onDismiss()
            })
            QuietButton(
                if (child.archived) "Bring back ${child.name}" else "Archive ${child.name}",
                onClick = { perform(FamilyActions.editChild(child.id, archived = !child.archived)); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Archiving hides a child. Their history is kept.", style = Hp.type.caption, color = Hp.colors.inkMuted)
        }
    }
}

/** SPEC FR-8: the values behaviour awards are linked to. */
@Composable
fun ValuesSettings(state: FamilyState, onBack: () -> Unit, perform: (Action) -> Unit) {
    var editing by remember { mutableStateOf<ValueRecord?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        TopBar("Family values", onBack)
        Text(
            "Awards are for noticing these. Each award names one and says what happened.",
            style = Hp.type.body, color = Hp.colors.inkMuted, modifier = Modifier.padding(Space.l),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Rule()
            state.values.filter { !it.archived }.forEach { value ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Hp.colors.surface).clickable { editing = value }.padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HpIcons.of(value.icon), contentDescription = null, tint = Hp.colors.ink, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(Space.m))
                    Text(value.name, style = Hp.type.body, color = Hp.colors.ink, modifier = Modifier.weight(1f))
                    Icon(HpIcons.Edit, contentDescription = null, tint = Hp.colors.inkMuted, modifier = Modifier.size(20.dp))
                }
                Rule()
            }
        }
        OutcomeButton("Add a value", { adding = true }, icon = HpIcons.Plus, modifier = Modifier.navigationBarsPadding().padding(Space.l))
    }
    if (adding || editing != null) {
        ValueEditSheet(editing, onDismiss = { adding = false; editing = null }, perform = perform)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValueEditSheet(value: ValueRecord?, onDismiss: () -> Unit, perform: (Action) -> Unit) {
    var name by rememberSaveable { mutableStateOf(value?.name ?: "") }
    var icon by remember { mutableStateOf(value?.icon ?: IconKey("heart")) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Hp.colors.surface, shape = Radius.sheet) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Text(if (value == null) "New value" else "Edit ${value.name}", style = Hp.type.headline, color = Hp.colors.ink)
            Field("Name", name, { name = it })
            IconGrid(VALUE_ICONS, icon) { icon = it }
            OutcomeButton("Save", enabled = name.isNotBlank(), onClick = { perform(FamilyActions.saveValue(value?.id, name, icon)); onDismiss() })
            if (value != null) QuietButton("Remove ${value.name}", { perform(FamilyActions.archiveValue(value.id)); onDismiss() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun IconGrid(keys: List<String>, selected: IconKey, columns: Int = 6, onSelect: (IconKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        keys.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                row.forEach { key -> PickTile(HpIcons.of(IconKey(key)), "", selected.key == key, onClick = { onSelect(IconKey(key)) }, modifier = Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val VALUE_ICONS = listOf("heart", "shield", "mountain", "star", "people", "smile", "book", "music", "gift", "plant", "dog", "pencil")

/** SPEC FR-54: the family's rewards shop. */
@Composable
fun RewardsSettings(state: FamilyState, formats: Formats, onBack: () -> Unit, perform: (Action) -> Unit) {
    var editing by remember { mutableStateOf<RewardRecord?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        TopBar("Rewards shop", onBack)
        Text(
            "Treats a child can spend points on instead of money: screen time, choosing dinner, a trip out. Spending one is recorded like a cash-out.",
            style = Hp.type.body, color = Hp.colors.inkMuted, modifier = Modifier.padding(Space.l),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Rule()
            state.rewards.filter { !it.archived }.forEach { reward ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).background(Hp.colors.surface).clickable { editing = reward }.padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HpIcons.of(reward.icon), contentDescription = null, tint = Hp.colors.ink, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(Space.m))
                    Text(reward.title, style = Hp.type.body, color = Hp.colors.ink, modifier = Modifier.weight(1f))
                    Text(formats.points(reward.price), style = Hp.type.figure, color = Hp.colors.ink)
                }
                Rule()
            }
        }
        OutcomeButton("Add a reward", { adding = true }, icon = HpIcons.Plus, modifier = Modifier.navigationBarsPadding().padding(Space.l))
    }
    if (adding || editing != null) RewardEditSheet(editing, formats, onDismiss = { adding = false; editing = null }, perform = perform)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardEditSheet(reward: RewardRecord?, formats: Formats, onDismiss: () -> Unit, perform: (Action) -> Unit) {
    var title by rememberSaveable { mutableStateOf(reward?.title ?: "") }
    var icon by remember { mutableStateOf(reward?.icon ?: IconKey("gift")) }
    var price by rememberSaveable { mutableStateOf(reward?.price?.value ?: DEFAULT_PRICE) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Hp.colors.surface, shape = Radius.sheet) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Text(if (reward == null) "New reward" else "Edit ${reward.title}", style = Hp.type.headline, color = Hp.colors.ink)
            Field("What", title, { title = it }, placeholder = "e.g. Screen time, 30 minutes")
            IconGrid(REWARD_ICONS, icon) { icon = it }
            Stepper("Price", formats.points(Points(price)), onMinus = { price = (price - PRICE_STEP).coerceAtLeast(PRICE_STEP) }, onPlus = { price += PRICE_STEP })
            OutcomeButton("Save", enabled = title.isNotBlank(), onClick = { perform(FamilyActions.saveReward(reward?.id, title, icon, Points(price))); onDismiss() })
            if (reward != null) QuietButton("Remove ${reward.title}", { perform(FamilyActions.archiveReward(reward.id)); onDismiss() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val REWARD_ICONS = listOf("gift", "music", "book", "bike", "kite", "headphones", "star", "smile", "table", "dog", "car", "plant")
private const val DEFAULT_PRICE = 50L
private const val PRICE_STEP = 10L
