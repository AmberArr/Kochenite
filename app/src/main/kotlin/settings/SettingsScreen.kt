package moe.frost.kochenite.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.Tag
import moe.frost.kochenite.transactions.categoryIcon
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.AppTopBar
import moe.frost.kochenite.ui.components.Avatar
import moe.frost.kochenite.ui.components.ContentMaxWidth
import moe.frost.kochenite.ui.components.CurrencySheet
import moe.frost.kochenite.ui.components.DropdownField
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.GroupCard
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.ListRow
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SectionHeader
import moe.frost.kochenite.ui.components.Segmented
import moe.frost.kochenite.ui.components.SwitchRow
import moe.frost.kochenite.ui.components.symbol
import java.util.UUID

@Composable
private fun muted() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun Chevron() = AppIcon(R.drawable.ic_chevron_right, null, tint = muted())

/** Grouped list settings. Privacy rows state plainly what does and does not leave the device. */
@Composable
internal fun SettingsScreen(
    state: BookState,
    model: BookViewModel,
    onBack: () -> Unit,
    categories: () -> Unit,
    tags: () -> Unit,
    templates: () -> Unit,
    groups: () -> Unit,
) {
    var choosingCurrency by rememberSaveable { mutableStateOf(false) }
    val display = state.settings?.displayCurrency
    AppScaffold(topBar = {
        AppTopBar(stringResource(R.string.settings), onBack = onBack, backEnabled = !state.saving)
    }, maxWidth = ContentMaxWidth) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            SectionHeader(stringResource(R.string.general))
            GroupCard {
                ListRow(
                    stringResource(R.string.display_currency),
                    Modifier.testTag("display-currency"),
                    supporting = stringResource(R.string.display_currency_hint),
                    leading = { AppIcon(R.drawable.ic_currency, null, tint = muted()) },
                    trailing = {
                        Text(
                            display?.let { "${it.symbol().trim()} ${it.code}" }.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = muted(),
                        )
                        AppIcon(R.drawable.ic_chevron_right, null, tint = muted())
                    },
                    enabled = !state.saving,
                ) { choosingCurrency = true }
            }
            SectionHeader(stringResource(R.string.organize))
            GroupCard {
                ListRow(
                    stringResource(R.string.categories),
                    Modifier.testTag("settings-categories"),
                    supporting = stringResource(R.string.categories_hint),
                    leading = { AppIcon(R.drawable.ic_category, null, tint = muted()) },
                    trailing = { Chevron() },
                    onClick = categories,
                )
                ListRow(
                    stringResource(R.string.tags),
                    Modifier.testTag("settings-tags"),
                    supporting = stringResource(R.string.tags_summary, state.tags.count { !it.archived }, state.tags.count { it.archived }),
                    leading = { AppIcon(R.drawable.ic_label, null, tint = muted()) },
                    trailing = { Chevron() },
                    onClick = tags,
                )
                ListRow(
                    stringResource(R.string.templates),
                    Modifier.testTag("settings-templates"),
                    supporting = pluralStringResource(R.plurals.saved_count, state.templates.size, state.templates.size),
                    leading = { AppIcon(R.drawable.ic_bookmark, null, tint = muted()) },
                    trailing = { Chevron() },
                    onClick = templates,
                )
                ListRow(
                    stringResource(R.string.groups_and_order),
                    Modifier.testTag("settings-groups"),
                    supporting = pluralStringResource(R.plurals.group_count, state.groups.size, state.groups.size),
                    leading = { AppIcon(R.drawable.ic_folder, null, tint = muted()) },
                    trailing = { Chevron() },
                    onClick = groups,
                )
            }
            SectionHeader(stringResource(R.string.data))
            GroupCard {
                // Backup and restore reserve their place until they are built.
                ListRow(
                    stringResource(R.string.back_up_to_file),
                    supporting = stringResource(R.string.back_up_hint),
                    leading = { AppIcon(R.drawable.ic_upload, null) },
                    enabled = false,
                )
                ListRow(
                    stringResource(R.string.restore_from_file),
                    supporting = stringResource(R.string.restore_hint),
                    leading = { AppIcon(R.drawable.ic_download, null) },
                    enabled = false,
                )
            }
            SectionHeader(stringResource(R.string.privacy))
            GroupCard {
                ListRow(
                    stringResource(R.string.records_stay),
                    supporting = stringResource(R.string.records_stay_hint),
                    leading = { AppIcon(R.drawable.ic_lock, null, tint = muted()) },
                )
                ListRow(
                    stringResource(R.string.crash_diagnostics),
                    supporting = stringResource(R.string.crash_diagnostics_hint),
                    leading = { AppIcon(R.drawable.ic_visibility, null, tint = muted()) },
                )
            }
        }
    }
    if (choosingCurrency) {
        CurrencySheet(display, state.accounts.map { it.account.currency }, { choosingCurrency = false }) {
            model.display(it)
            choosingCurrency = false
        }
    }
}

/** Kind tabs keep income and expense trees apart; children sit indented under their parent. */
@Composable
internal fun CategoriesScreen(
    state: BookState,
    onBack: () -> Unit,
    edit: (String?) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(CategoryKind.EXPENSE) }
    val ofKind = state.categories.filter { it.kind == kind }.sortedBy { it.name.lowercase() }
    val active = ofKind.filter { !it.archived }
    val archived = ofKind.filter { it.archived }
    AppScaffold(
        topBar = {
            AppTopBar(stringResource(R.string.categories), onBack = onBack) {
                IconAction(R.drawable.ic_add, stringResource(R.string.new_category), Modifier.testTag("new-category")) { edit(null) }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Segmented(
                listOf(CategoryKind.EXPENSE to stringResource(R.string.expense), CategoryKind.INCOME to stringResource(R.string.income)),
                kind,
            ) { kind = it }
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            GroupCard {
                ListRow(
                    stringResource(if (kind == CategoryKind.INCOME) R.string.uncategorized_income else R.string.uncategorized_expense),
                    supporting = stringResource(R.string.uncategorized_hint),
                    leading = { AppIcon(R.drawable.ic_lock, null, tint = muted()) },
                )
            }
            val parents = active.filter { it.parentId == null }
            // A child whose parent is archived still needs a place to be found and edited.
            val orphans = active.filter { child -> child.parentId != null && parents.none { it.id == child.parentId } }
            if (parents.isNotEmpty() || orphans.isNotEmpty()) {
                GroupCard {
                    parents.forEach { parent ->
                        val children = active.filter { it.parentId == parent.id }
                        ListRow(
                            parent.name,
                            Modifier.testTag("category-${parent.id}"),
                            supporting =
                                if (children.isEmpty()) {
                                    null
                                } else {
                                    pluralStringResource(
                                        R.plurals.subcategory_count,
                                        children.size,
                                        children.size,
                                    )
                                },
                            leading = { Avatar(categoryIcon(parent, state.categories)) },
                            trailing = { Chevron() },
                        ) { edit(parent.id) }
                        children.forEach { child ->
                            ListRow(
                                child.name,
                                Modifier.testTag("category-${child.id}"),
                                trailing = { Chevron() },
                                startPadding = 72.dp,
                            ) { edit(child.id) }
                        }
                    }
                    orphans.forEach { child ->
                        ListRow(
                            child.name,
                            Modifier.testTag("category-${child.id}"),
                            trailing = { Chevron() },
                            startPadding = 72.dp,
                        ) { edit(child.id) }
                    }
                }
            }
            if (archived.isNotEmpty()) {
                Column {
                    SectionHeader(stringResource(R.string.archived))
                    GroupCard {
                        archived.forEach { category ->
                            ListRow(
                                category.name,
                                Modifier.testTag("category-${category.id}"),
                                supporting = stringResource(R.string.archived_category_hint),
                                leading = { AppIcon(R.drawable.ic_archive, null, tint = muted()) },
                                trailing = { Chevron() },
                            ) { edit(category.id) }
                        }
                    }
                }
            }
        }
    }
}

/** Constraints are explained where they apply; a rejected save keeps the input and says why. */
@Composable
internal fun CategoryFormScreen(
    id: String?,
    state: BookState,
    model: BookViewModel,
    onBack: () -> Unit,
) {
    val old = remember(id) { state.categories.find { it.id == id } }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(old?.name ?: "") }
    var kind by rememberSaveable(id) { mutableStateOf(old?.kind ?: CategoryKind.EXPENSE) }
    var parent by rememberSaveable(id) { mutableStateOf(old?.parentId) }
    var archived by rememberSaveable(id) { mutableStateOf(old?.archived ?: false) }
    AppScaffold(
        topBar = {
            AppTopBar(
                stringResource(
                    if (old ==
                        null
                    ) {
                        R.string.new_category
                    } else {
                        R.string.edit_category
                    },
                ),
                onBack = onBack,
                close = true,
                backEnabled = !state.saving,
            ) {
                TextButton(
                    onClick = { model.saveCategory(Category(old?.id ?: newId, name.trim(), kind, parent, archived), old) },
                    enabled = !state.saving && name.isNotBlank(),
                    modifier = Modifier.testTag("save-category"),
                ) { Text(stringResource(R.string.save)) }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Field(R.string.category_name, name, { name = it }, enabled = !state.saving)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.kind), style = MaterialTheme.typography.labelLarge, color = muted())
                Segmented(
                    listOf(
                        CategoryKind.EXPENSE to stringResource(R.string.expense),
                        CategoryKind.INCOME to stringResource(R.string.income),
                    ),
                    kind,
                    enabled = !state.saving,
                ) {
                    if (it != kind) {
                        kind = it
                        // A parent must share the kind.
                        parent = null
                    }
                }
                if (old !=
                    null
                ) {
                    Text(stringResource(R.string.category_kind_hint), style = MaterialTheme.typography.bodySmall, color = muted())
                }
            }
            DropdownField(
                stringResource(R.string.category_parent),
                state.categories.find { it.id == parent }?.name ?: stringResource(R.string.no_parent),
                listOf(null to stringResource(R.string.no_parent)) +
                    state.categories
                        .filter { it.parentId == null && it.id != id && it.kind == kind && !it.archived }
                        .sortedBy { it.name.lowercase() }
                        .map { it.id to it.name },
                supporting = stringResource(R.string.category_parent_hint),
                enabled = !state.saving,
                tag = "category-parent",
            ) { parent = it }
            if (old != null) {
                GroupCard {
                    SwitchRow(
                        stringResource(R.string.archived),
                        archived,
                        supporting = stringResource(R.string.archive_children_first),
                        enabled = !state.saving,
                    ) {
                        archived = it
                    }
                }
            }
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
        }
    }
}

/** Flat tags with rename and archive. Archived tags stay on history but cannot be newly applied. */
@Composable
internal fun TagsScreen(
    state: BookState,
    model: BookViewModel,
    onBack: () -> Unit,
) {
    // Null: no sheet. Empty: a new tag. Otherwise the tag being edited.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val active = state.tags.filter { !it.archived }
    val archived = state.tags.filter { it.archived }
    AppScaffold(
        topBar = {
            AppTopBar(stringResource(R.string.tags), onBack = onBack) {
                IconAction(R.drawable.ic_add, stringResource(R.string.new_tag), Modifier.testTag("new-tag")) { editing = "" }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            if (state.tags.isEmpty()) {
                Text(stringResource(R.string.no_tags), style = MaterialTheme.typography.bodyMedium, color = muted())
            }
            if (active.isNotEmpty()) {
                GroupCard {
                    active.forEach { tag ->
                        ListRow(
                            tag.name,
                            Modifier.testTag("tag-${tag.id}"),
                            leading = { AppIcon(R.drawable.ic_label, null, tint = muted()) },
                            trailing = { AppIcon(R.drawable.ic_edit, null, tint = muted()) },
                        ) { editing = tag.id }
                    }
                }
            }
            if (archived.isNotEmpty()) {
                Column {
                    SectionHeader(stringResource(R.string.archived))
                    GroupCard {
                        archived.forEach { tag ->
                            ListRow(
                                tag.name,
                                Modifier.testTag("tag-${tag.id}"),
                                supporting = stringResource(R.string.archived_tag_hint),
                                leading = { AppIcon(R.drawable.ic_archive, null, tint = muted()) },
                                trailing = { AppIcon(R.drawable.ic_edit, null, tint = muted()) },
                            ) { editing = tag.id }
                        }
                    }
                }
            }
        }
    }
    editing?.let { id -> TagSheet(state.tags.find { it.id == id }, state, model) { editing = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSheet(
    old: Tag?,
    state: BookState,
    model: BookViewModel,
    close: () -> Unit,
) {
    val newId = rememberSaveable(old?.id) { UUID.randomUUID().toString() }
    var name by rememberSaveable(old?.id) { mutableStateOf(old?.name ?: "") }
    var archived by rememberSaveable(old?.id) { mutableStateOf(old?.archived ?: false) }
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
                stringResource(if (old == null) R.string.new_tag else R.string.edit_tag),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Field(R.string.tag_name, name, { name = it }, enabled = !state.saving)
            if (old != null) {
                SwitchRow(stringResource(R.string.archived), archived, supporting = stringResource(R.string.archived_tag_hint)) {
                    archived =
                        it
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = close) { Text(stringResource(R.string.cancel)) }
                Button(
                    // The sheet closes at once; a rejected save shows its reason on the screen underneath.
                    onClick = {
                        model.saveTag(Tag(old?.id ?: newId, name.trim(), archived), old)
                        close()
                    },
                    enabled = !state.saving && name.isNotBlank(),
                    modifier = Modifier.testTag("save-tag"),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
