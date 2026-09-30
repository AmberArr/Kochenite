package moe.frost.kochenite.transactions.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.frost.kochenite.BookState
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.editor.addItem
import moe.frost.kochenite.domain.editor.moveItem
import moe.frost.kochenite.domain.editor.removeItem
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.ReceiptLine
import moe.frost.kochenite.transactions.categoryPath
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.AppTopBar
import moe.frost.kochenite.ui.components.ContentMaxWidth
import moe.frost.kochenite.ui.components.DropdownField
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.SignDisplay
import moe.frost.kochenite.ui.components.StatusBadge
import moe.frost.kochenite.ui.components.cardColor
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.symbol
import moe.frost.kochenite.ui.components.tabular

/** Item edits stay in the editor's memory until the parent transaction is saved. */
@Composable
internal fun TransactionItemsScreen(
    state: BookState,
    model: TransactionEditorStateOwner,
    editor: EntryEditorState,
    onClose: () -> Unit,
) {
    val current = editor.input
    val accounts = state.accounts.map { it.account }
    val account = accounts.find { it.id == current.input.accountId }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    val itemDeleted = stringResource(R.string.item_deleted)
    val undo = stringResource(R.string.undo)
    val uncategorized = stringResource(R.string.uncategorized)
    val reconciliation = reconcile(current, accounts, state.categories)
    val currency = reconciliation.currency
    val title = current.input.merchant.ifBlank { stringResource(R.string.new_transaction) }

    fun remove(
        index: Int,
        line: ReceiptLine,
    ) {
        val removedRow = current.input.allocations[index]
        val text = current.lineTexts[line.id].orEmpty()
        model.showItem(null)
        model.updateEditor { it.removeItem(line.id, accounts) }
        scope.launch {
            if (snackbar.showSnackbar(itemDeleted, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                model.updateEditor { latest ->
                    val lines = latest.input.receiptLines.toMutableList()
                    val rows = if (lines.isEmpty()) mutableListOf() else latest.input.allocations.toMutableList()
                    val at = index.coerceAtMost(lines.size)
                    lines.add(at, line)
                    rows.add(at, removedRow)
                    latest.copy(
                        input = latest.input.copy(receiptLines = lines, allocations = rows),
                        lineTexts = latest.lineTexts + (line.id to text),
                    )
                }
            }
        }
    }
    AppScaffold(
        topBar = { AppTopBar(stringResource(R.string.transaction_items), onBack = onClose) },
        maxWidth = ContentMaxWidth,
        bottomBar =
            {
                Button(onClick = { model.showItems(false) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("items-done")) {
                    Text(stringResource(R.string.done))
                }
            },
    ) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "summary") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        listOfNotNull(title, account?.name).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ReconciliationCard(reconciliation)
                }
            }
            if (current.input.receiptLines.isNotEmpty()) {
                item(key = "items") {
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cardColor())) {
                        current.input.receiptLines.forEachIndexed { index, line ->
                            val allocation = current.input.allocations.getOrNull(index)
                            val categoryName =
                                state.categories.find { it.id == allocation?.categoryId }?.let { categoryPath(it, state.categories) }
                                    ?: uncategorized
                            val invalidCategory = line.id in reconciliation.invalidCategoryItems
                            val moveDescription = stringResource(R.string.move_item, line.description.ifBlank { categoryName })
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                                var drag by remember(line.id) { mutableFloatStateOf(0f) }
                                Box(
                                    Modifier
                                        .size(56.dp)
                                        .semantics {
                                            contentDescription = moveDescription
                                            customActions =
                                                listOf(
                                                    CustomAccessibilityAction(moveUp) {
                                                        model.updateEditor { it.moveItem(line.id, -1, accounts) }
                                                        true
                                                    },
                                                    CustomAccessibilityAction(moveDown) {
                                                        model.updateEditor { it.moveItem(line.id, 1, accounts) }
                                                        true
                                                    },
                                                )
                                        }.pointerInput(line.id) {
                                            detectDragGestures(
                                                onDragStart = { drag = 0f },
                                                onDragEnd = { drag = 0f },
                                                onDragCancel = { drag = 0f },
                                            ) { change, amount ->
                                                change.consume()
                                                drag += amount.y
                                                val threshold = 56.dp.toPx()
                                                if (kotlin.math.abs(drag) >= threshold) {
                                                    model.updateEditor { it.moveItem(line.id, if (drag > 0) 1 else -1, accounts) }
                                                    drag = 0f
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AppIcon(R.drawable.ic_drag, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = 64.dp)
                                        .testTag("transaction-item-$index")
                                        .clickable { model.showItem(line.id) }
                                        .semantics(mergeDescendants = true) {}
                                        .padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            line.description.ifBlank { categoryName },
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val scheme = MaterialTheme.colorScheme
                                        val tint = if (invalidCategory) scheme.error else scheme.onSurfaceVariant
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AppIcon(R.drawable.ic_category, null, Modifier.size(16.dp), tint = tint)
                                            Text(
                                                if (invalidCategory) stringResource(R.string.choose_category) else categoryName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = tint,
                                            )
                                        }
                                    }
                                    val amount = runCatching { currency?.parseMinor(current.lineTexts[line.id].orEmpty()) }.getOrNull()
                                    Text(
                                        amount?.let { currency?.display(it) } ?: "—",
                                        style = MaterialTheme.typography.titleMedium.tabular,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "add") {
                OutlinedButton(
                    onClick = {
                        model.updateEditor { it.addItem(accounts) }
                        model.showItem(
                            model.editor.value
                                ?.input
                                ?.input
                                ?.receiptLines
                                ?.lastOrNull()
                                ?.id,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add-item"),
                ) {
                    AppIcon(R.drawable.ic_add, null, Modifier.padding(end = 8.dp).size(18.dp))
                    Text(stringResource(R.string.add_item))
                }
            }
        }
        SnackbarHost(snackbar)
    }
    val index = current.input.receiptLines.indexOfFirst { it.id == editor.itemId }
    if (index >= 0) ItemSheet(state, model, editor, index) { remove(index, current.input.receiptLines[index]) }
}

/** Status is text and a badge with an icon; the error tone only reinforces it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReconciliationCard(reconciliation: Reconciliation) {
    val scheme = MaterialTheme.colorScheme
    val mismatch = !reconciliation.pending && !reconciliation.matched
    val content = if (mismatch) scheme.onErrorContainer else scheme.onSurface
    val muted = if (mismatch) scheme.onErrorContainer else scheme.onSurfaceVariant
    val currency = reconciliation.currency
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (mismatch) scheme.errorContainer else cardColor())
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.item_count, reconciliation.count, reconciliation.count),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                color = content,
            )
            when {
                reconciliation.matched -> {
                    StatusBadge(stringResource(R.string.totals_match), ok = true)
                }

                reconciliation.pending -> {
                    Text(stringResource(R.string.amounts_pending), style = MaterialTheme.typography.labelMedium, color = muted)
                }

                reconciliation.difference != 0L -> {
                    StatusBadge(stringResource(R.string.totals_mismatch), ok = false)
                }

                else -> {
                    StatusBadge(stringResource(R.string.item_categories_error), ok = false)
                }
            }
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Value(
                stringResource(R.string.items_sum),
                reconciliation.sum?.let { currency?.display(it) } ?: "—",
                content,
                muted,
                Alignment.Start,
            )
            Value(
                stringResource(R.string.amount),
                reconciliation.target?.let {
                    currency?.display(it)
                } ?: "—",
                content,
                muted,
                Alignment.CenterHorizontally,
            )
            Value(
                stringResource(R.string.difference),
                reconciliation.difference?.let { currency?.display(it, if (it == 0L) SignDisplay.NONE else SignDisplay.ALWAYS) } ?: "—",
                content,
                muted,
                Alignment.End,
            )
        }
        val sum = reconciliation.sum
        val target = reconciliation.target
        if (sum != null && target != null && target > 0) {
            LinearProgressIndicator(
                progress = {
                    (
                        sum.toBigDecimal().divide(
                            target.toBigDecimal(),
                            4,
                            java.math.RoundingMode.DOWN,
                        )
                    ).toFloat().coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = if (mismatch) scheme.error else scheme.primary,
                trackColor = (if (mismatch) scheme.error else scheme.primary).copy(alpha = 0.3f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        if (reconciliation.allocationPending) {
            Text(stringResource(R.string.account_allocation_pending), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
private fun Value(
    label: String,
    value: String,
    color: Color,
    muted: Color,
    alignment: Alignment.Horizontal,
) {
    Column(horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
        Text(value, style = MaterialTheme.typography.titleMedium.tabular, color = color, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(
    state: BookState,
    model: TransactionEditorStateOwner,
    editor: EntryEditorState,
    index: Int,
    delete: () -> Unit,
) {
    val current = editor.input
    val item = current.input.receiptLines[index]
    val allocation = current.input.allocations.getOrNull(index)
    val accounts = state.accounts.map { it.account }
    val account = accounts.find { it.id == current.input.accountId }
    val kind = if (current.input.kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { !it.archived && it.kind == kind }.sortedBy { categoryPath(it, state.categories).lowercase() }
    val uncategorized = stringResource(R.string.uncategorized)
    val foreign = current.transactionCurrency != account?.currency
    ModalBottomSheet(
        onDismissRequest = { model.showItem(null) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.item),
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                IconAction(
                    R.drawable.ic_delete,
                    stringResource(R.string.delete_item, index + 1),
                    Modifier.testTag("delete-item"),
                    onClick = delete,
                )
            }
            Field(
                R.string.transaction_title,
                item.description,
                { text ->
                    model.updateEditor {
                        it.copy(
                            input =
                                it.input.copy(
                                    receiptLines =
                                        it.input.receiptLines.map { line ->
                                            if (line.id ==
                                                item.id
                                            ) {
                                                line.copy(description = text)
                                            } else {
                                                line
                                            }
                                        },
                                ),
                        )
                    }
                },
                clearable = true,
            )
            DropdownField(
                stringResource(R.string.category),
                categories.find { it.id == allocation?.categoryId }?.let { categoryPath(it, state.categories) } ?: uncategorized,
                listOf(null to uncategorized) + categories.map { it.id to categoryPath(it, state.categories) },
                tag = "choice-Category",
            ) { id ->
                model.updateEditor {
                    it.copy(
                        input =
                            it.input.copy(
                                allocations =
                                    it.input.allocations.map { row ->
                                        if (row.id ==
                                            item.id
                                        ) {
                                            row.copy(categoryId = id)
                                        } else {
                                            row
                                        }
                                    },
                            ),
                    )
                }
            }
            Field(
                R.string.amount,
                current.lineTexts[item.id].orEmpty(),
                { text -> model.updateEditor { it.copy(lineTexts = it.lineTexts + (item.id to text)) } },
                number = true,
                prefix = current.transactionCurrency?.symbol(),
            )
            // Foreign lines show the share of the actual account charge they were allocated.
            Field(
                R.string.account_allocation,
                if (foreign && allocation != null && current.input.amountMinor != null) {
                    account?.currency?.formatMinor(allocation.amountMinor).orEmpty()
                } else {
                    "—"
                },
                {},
                enabled = false,
                prefix = account?.currency?.symbol(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { model.showItem(null) }, modifier = Modifier.testTag("transaction-item-done")) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}
