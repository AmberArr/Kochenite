package moe.frost.kochenite.accounts

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.AppTopBar
import moe.frost.kochenite.ui.components.ContentMaxWidth
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.GroupCard
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import java.util.UUID
import kotlin.math.abs

/** Moves the element at [index] by [offset] places, or returns the list unchanged at either end. */
internal fun <T> List<T>.moved(
    index: Int,
    offset: Int,
): List<T> {
    val target = index + offset
    if (index !in indices || target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}

/**
 * A drag handle that moves its row one place each time the drag passes a row's height, with Move up
 * and Move down exposed as accessibility actions.
 */
@Composable
private fun MoveHandle(
    description: String,
    key: Any,
    enabled: Boolean,
    move: (Int) -> Unit,
) {
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    var drag by remember(key) { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .size(48.dp)
            .semantics {
                contentDescription = description
                customActions =
                    listOf(
                        CustomAccessibilityAction(moveUp) {
                            move(-1)
                            true
                        },
                        CustomAccessibilityAction(moveDown) {
                            move(1)
                            true
                        },
                    )
            }.pointerInput(key, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(onDragStart = { drag = 0f }, onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f }) { change, amount ->
                    change.consume()
                    drag += amount.y
                    if (abs(drag) >= 56.dp.toPx()) {
                        move(if (drag > 0) 1 else -1)
                        drag = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(R.drawable.ic_drag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Direct manipulation replaces numeric order fields. Every move is stored as one atomic reorder. */
@Composable
internal fun GroupsScreen(
    state: BookState,
    model: AccountStateOwner,
    onBack: () -> Unit,
) {
    // Null: no sheet. Empty: a new group. Otherwise the group being renamed.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = state.groups
    val accounts = state.accounts.map { it.account }
    val sections: List<Pair<AccountGroup?, List<Account>>> =
        groups.map { group -> group to accounts.filter { it.groupId == group.id } } +
            listOf(null to accounts.filter { account -> groups.none { it.id == account.groupId } })

    fun reorder(
        groupOrder: List<AccountGroup>,
        sectionOrder: List<Pair<AccountGroup?, List<Account>>>,
    ) = model.reorder(groupOrder.map { it.id }, sectionOrder.flatMap { it.second }.map { it.id })
    AppScaffold(
        topBar = {
            AppTopBar(stringResource(R.string.groups_and_order), onBack = onBack, backEnabled = !state.saving) {
                IconAction(R.drawable.ic_add, stringResource(R.string.new_group), Modifier.testTag("new-group")) { editing = "" }
            }
        },
        maxWidth = ContentMaxWidth,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.groups_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            sections.forEachIndexed { sectionIndex, (group, members) ->
                if (group == null && members.isEmpty()) return@forEachIndexed
                GroupCard {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (group != null) {
                            MoveHandle(stringResource(R.string.move_item, group.name), group.id, !state.saving) { offset ->
                                val index = groups.indexOf(group)
                                val next = groups.moved(index, offset)
                                if (next !=
                                    groups
                                ) {
                                    reorder(next, next.map { moved -> sections.first { it.first == moved } } + sections.last())
                                }
                            }
                        } else {
                            Box(Modifier.size(48.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                group?.name ?: stringResource(R.string.no_group),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                pluralStringResource(R.plurals.account_count, members.size, members.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (group != null) {
                            IconAction(
                                R.drawable.ic_edit,
                                stringResource(R.string.edit_group_named, group.name),
                                Modifier.testTag("edit-group-${group.id}"),
                                enabled = !state.saving,
                            ) { editing = group.id }
                        }
                    }
                    members.forEachIndexed { index, account ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 36.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MoveHandle(stringResource(R.string.move_item, account.name), account.id, !state.saving) { offset ->
                                val next = members.moved(index, offset)
                                if (next != members) {
                                    reorder(
                                        groups,
                                        sections.mapIndexed { at, section ->
                                            if (at ==
                                                sectionIndex
                                            ) {
                                                section.first to next
                                            } else {
                                                section
                                            }
                                        },
                                    )
                                }
                            }
                            Text(account.name, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
    editing?.let { id ->
        GroupSheet(groups.find { it.id == id }, state, model, (groups.maxOfOrNull { it.position } ?: -1) + 1) { editing = null }
    }
}

/** A short sheet: one field does not need a full screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSheet(
    old: AccountGroup?,
    state: BookState,
    model: AccountStateOwner,
    nextPosition: Int,
    close: () -> Unit,
) {
    var name by rememberSaveable(old?.id) { mutableStateOf(old?.name ?: "") }
    val newId = rememberSaveable(old?.id) { UUID.randomUUID().toString() }
    ModalBottomSheet(onDismissRequest = close, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (old == null) R.string.new_group else R.string.edit_group),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Field(R.string.group_name, name, { name = it }, enabled = !state.saving)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = close) { Text(stringResource(R.string.cancel)) }
                Button(
                    // The sheet closes at once; a rejected save shows its reason on the screen underneath.
                    onClick = {
                        model.saveGroup(AccountGroup(old?.id ?: newId, name.trim(), old?.position ?: nextPosition), old)
                        close()
                    },
                    enabled = !state.saving && name.isNotBlank(),
                    modifier = Modifier.testTag("save-group"),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
