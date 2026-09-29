package moe.frost.kochenite.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.Tag
import moe.frost.kochenite.ui.components.Choice
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.Form
import moe.frost.kochenite.ui.components.Toggle
import moe.frost.kochenite.ui.components.currency
import java.util.UUID

@Composable internal fun SettingsForm(
    state: BookState,
    model: BookViewModel,
    categories: () -> Unit,
    tags: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf(state.settings?.displayCurrency?.code ?: "") }
    Form(R.string.settings, state) {
        Field(R.string.display_currency, code, { code = it })
        Button(onClick = {
            currency(code)?.let(model::display) ?: model.error(R.string.currency_error)
        }, enabled = !state.saving) { Text(stringResource(R.string.save)) }
        TextButton(onClick = categories) { Text(stringResource(R.string.categories)) }
        TextButton(onClick = tags) { Text(stringResource(R.string.tags)) }
    }
}

@Composable internal fun TagsPanel(
    state: BookState,
    edit: (String?) -> Unit,
) {
    Form(R.string.tags, state) {
        TextButton(onClick = { edit(null) }) { Text(stringResource(R.string.new_tag)) }
        state.tags.forEach { tag ->
            TextButton(onClick = { edit(tag.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tag.name) }
            if (tag.archived) Text(stringResource(R.string.archived))
        }
    }
}

@Composable internal fun TagForm(
    id: String?,
    state: BookState,
    model: BookViewModel,
) {
    val old = remember(id) { state.tags.find { it.id == id } }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(old?.name ?: "") }
    var archived by rememberSaveable(id) { mutableStateOf(old?.archived ?: false) }
    Form(R.string.tags, state) {
        Field(R.string.tag_name, name, { name = it })
        if (old != null) Toggle(R.string.archived, archived) { archived = it }
        Button(onClick = { model.saveTag(Tag(old?.id ?: newId, name.trim(), archived), old) }, enabled = !state.saving) {
            Text(stringResource(R.string.save))
        }
    }
}

@Composable internal fun CategoriesPanel(
    state: BookState,
    edit: (String?) -> Unit,
) {
    Form(R.string.categories, state) {
        TextButton(onClick = { edit(null) }) { Text(stringResource(R.string.new_category)) }
        Text(stringResource(R.string.uncategorized_income))
        Text(stringResource(R.string.uncategorized_expense))
        Text(stringResource(R.string.uncategorized_hint), style = MaterialTheme.typography.bodySmall)
        state.categories.sortedBy { it.parentId ?: it.id }.forEach { category ->
            TextButton(onClick = { edit(category.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(listOfNotNull(state.categories.find { it.id == category.parentId }?.name, category.name).joinToString(" / "))
            }
            if (category.archived) Text(stringResource(R.string.archived))
        }
    }
}

@Composable internal fun CategoryForm(
    id: String?,
    state: BookState,
    model: BookViewModel,
) {
    val old = remember(id) { state.categories.find { it.id == id } }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(old?.name ?: "") }
    var kind by rememberSaveable(id) { mutableStateOf(old?.kind?.name ?: CategoryKind.EXPENSE.name) }
    var parent by rememberSaveable(id) { mutableStateOf(old?.parentId) }
    var archived by rememberSaveable(id) { mutableStateOf(old?.archived ?: false) }
    Form(R.string.categories, state) {
        Field(R.string.category_name, name, { name = it })
        Choice(
            R.string.category_kind,
            stringResource(if (kind == CategoryKind.INCOME.name) R.string.income else R.string.expense),
            listOf(
                CategoryKind.EXPENSE.name to stringResource(R.string.expense),
                CategoryKind.INCOME.name to stringResource(R.string.income),
            ),
        ) {
            kind = it!!
            parent = null
        }
        Choice(
            R.string.category_parent,
            state.categories.find { it.id == parent }?.name ?: stringResource(R.string.no_parent),
            listOf(null to stringResource(R.string.no_parent)) +
                state.categories.filter { it.parentId == null && it.id != id && it.kind.name == kind && !it.archived }.map {
                    it.id to
                        it.name
                },
        ) { parent = it }
        if (old != null) Toggle(R.string.archived, archived) { archived = it }
        Button(onClick = {
            model.saveCategory(Category(old?.id ?: newId, name.trim(), CategoryKind.valueOf(kind), parent, archived), old)
        }, enabled = !state.saving) { Text(stringResource(R.string.save)) }
    }
}
