package moe.frost.kochenite.transactions.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.Tag
import moe.frost.kochenite.transactions.entryTitle
import moe.frost.kochenite.transactions.kindLabel
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.ListRow
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SignDisplay
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.shortDate
import moe.frost.kochenite.ui.components.tabular
import java.util.UUID

@Composable
private fun SheetHeader(
    title: String,
    doneTag: String,
    done: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = done, modifier = Modifier.testTag(doneTag)) { Text(stringResource(R.string.done)) }
    }
}

/** Multi-select with inline creation. Archived tags stay on entries that have them but cannot be newly applied. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagsSheet(
    state: BookState,
    bookModel: BookViewModel,
    model: TransactionEditorStateOwner,
    current: EditorInput,
    close: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // A tag being created is selected as soon as the book reports it.
    var pending by remember { mutableStateOf<String?>(null) }
    val selected = current.input.tagIds

    fun toggle(id: String) =
        model.updateEditor { editor ->
            val ids = editor.input.tagIds
            editor.copy(input = editor.input.copy(tagIds = if (id in ids) ids - id else ids + id))
        }
    LaunchedEffect(state.tags, state.error, pending) {
        val created = state.tags.find { it.id == pending }
        if (created != null) {
            if (created.id !in selected) toggle(created.id)
            query = ""
        }
        // A failed creation leaves its error visible and lets the user try again.
        if (created != null || state.error != null) pending = null
    }
    val name = query.trim()
    val visible =
        state.tags
            .filter { name.isBlank() || it.name.contains(name, ignoreCase = true) }
            .sortedWith(compareBy({ it.archived && it.id !in selected }, { it.name.lowercase() }))
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            SheetHeader(stringResource(R.string.tags), "entry-tag-picker-done", close)
            OutlinedTextField(
                query,
                { query = it },
                label = { Text(stringResource(R.string.find_or_create_tag)) },
                leadingIcon = { AppIcon(R.drawable.ic_search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).testTag("entry-tag-search"),
            )
            state.error?.let { Notice(stringResource(it), Modifier.padding(horizontal = 24.dp), tone = NoticeTone.ERROR) }
            LazyColumn(Modifier.heightIn(max = 440.dp).testTag("entry-tag-picker-list")) {
                if (name.isNotEmpty() && state.tags.none { it.name.equals(name, ignoreCase = true) }) {
                    item(key = "create") {
                        ListRow(
                            stringResource(R.string.create_tag, name),
                            Modifier.testTag("entry-tag-create"),
                            headlineColor = MaterialTheme.colorScheme.primary,
                            leading = { AppIcon(R.drawable.ic_add, null) },
                            startPadding = 24.dp,
                            enabled = !state.saving && pending == null,
                        ) {
                            val id = UUID.randomUUID().toString()
                            pending = id
                            bookModel.saveTag(Tag(id, name), null)
                        }
                    }
                }
                items(visible, key = { it.id }) { tag ->
                    val checked = tag.id in selected
                    // An archived tag can be removed from this entry but not added to it.
                    val enabled = !tag.archived || checked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("entry-tag-${tag.id}")
                            .toggleable(checked, enabled = enabled, role = Role.Checkbox) { toggle(tag.id) }
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, onCheckedChange = null, enabled = enabled)
                        Column {
                            val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
                            Text(
                                if (tag.archived) stringResource(R.string.archived_name, tag.name) else tag.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = muted,
                            )
                            if (tag.archived) {
                                Text(stringResource(R.string.archived_tag_hint), style = MaterialTheme.typography.bodySmall, color = muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Links are context only: they never change amounts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RelatedSheet(
    state: BookState,
    bookModel: BookViewModel,
    model: TransactionEditorStateOwner,
    current: EditorInput,
    close: () -> Unit,
) {
    var query by remember(current.id) { mutableStateOf("") }
    val linked = current.input.relatedEntryIds
    val accounts = state.accounts.map { it.account }
    LaunchedEffect(query, linked) { bookModel.searchRelatedEntries(query, linked) }
    val candidates =
        state.relatedEntries
            .distinctBy { it.id }
            .filter { it.id != current.entryId && (it.status == EntryStatus.POSTED || it.id in linked) }
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            SheetHeader(stringResource(R.string.related_transactions), "related-done", close)
            OutlinedTextField(
                query,
                { query = it },
                label = { Text(stringResource(R.string.search_related)) },
                leadingIcon = { AppIcon(R.drawable.ic_search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).testTag("related-search"),
            )
            Text(
                stringResource(R.string.related_hint),
                Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.relatedError?.let {
                Notice(
                    stringResource(it),
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    tone = NoticeTone.ERROR,
                )
            }
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(candidates, key = { it.id }) { related ->
                    val checked = related.id in linked
                    val enabled = related.status == EntryStatus.POSTED || checked
                    val account = accounts.find { it.id == related.input.accountId }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .testTag("related-entry-${related.id}")
                            .toggleable(checked, enabled = enabled, role = Role.Checkbox) {
                                model.updateEditor { editor ->
                                    val ids = editor.input.relatedEntryIds
                                    editor.copy(
                                        input =
                                            editor.input.copy(
                                                relatedEntryIds =
                                                    if (checked) {
                                                        ids - related.id
                                                    } else {
                                                        ids +
                                                            related.id
                                                    },
                                            ),
                                    )
                                }
                            }.padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked, onCheckedChange = null, enabled = enabled)
                        Column(Modifier.weight(1f)) {
                            Text(
                                entryTitle(related.input, accounts, state.categories),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(shortDate(related.input.date), kindLabel(related.input), account?.name).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            account?.currency?.display(related.input.amountMinor ?: 0L, SignDisplay.ALWAYS).orEmpty(),
                            style = MaterialTheme.typography.titleMedium.tabular,
                        )
                    }
                }
                if (candidates.isEmpty() && !state.relatedLoading) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.no_related_transactions),
                            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.relatedLoading) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                if (state.relatedNext != null && !state.relatedLoading) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = bookModel::loadMoreRelatedEntries) {
                                Text(stringResource(R.string.load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}
