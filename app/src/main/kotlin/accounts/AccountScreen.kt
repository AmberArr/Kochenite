package moe.frost.kochenite.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.ui.components.Choice
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.Form
import moe.frost.kochenite.ui.components.Toggle
import moe.frost.kochenite.ui.components.currency
import java.util.UUID

@Composable internal fun AccountForm(
    id: String?,
    state: BookState,
    model: AccountStateOwner,
    adjust: (String) -> Unit,
) {
    val old = remember(id) { state.accounts.find { it.account.id == id }?.account }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(old?.name ?: "") }
    var code by rememberSaveable(id) { mutableStateOf(old?.currency?.code ?: "") }
    var opening by rememberSaveable(id) { mutableStateOf("0") }
    var type by rememberSaveable(id) { mutableStateOf(old?.type ?: "") }
    var order by rememberSaveable(id) { mutableStateOf((old?.position ?: 0).toString()) }
    var group by rememberSaveable(id) { mutableStateOf(old?.groupId) }
    var include by rememberSaveable(id) { mutableStateOf(old?.includeInNetWorth ?: true) }
    var archived by rememberSaveable(id) { mutableStateOf(old?.archived ?: false) }
    Form(if (old == null) R.string.new_account else R.string.edit_account, state) {
        Field(R.string.account_name, name, { name = it })
        Field(R.string.account_currency, code, { code = it }, enabled = old?.currencyLocked != true)
        if (old?.currencyLocked == true) Text(stringResource(R.string.currency_locked))
        if (old == null) Field(R.string.opening_balance, opening, { opening = it }, number = true)
        Field(R.string.account_type, type, { type = it })
        Field(R.string.position, order, { order = it }, number = true)
        Choice(
            R.string.group,
            state.groups.find { it.id == group }?.name ?: stringResource(R.string.no_group),
            listOf(null to stringResource(R.string.no_group)) + state.groups.map { it.id to it.name },
        ) { group = it }
        Toggle(R.string.include_net_worth, include) { include = it }
        if (old != null) Toggle(R.string.archived, archived) { archived = it }
        Button(onClick = {
            val curr = currency(code)
            if (curr == null) {
                model.error(R.string.currency_error)
            } else {
                try {
                    val candidate =
                        Account(
                            old?.id ?: newId,
                            name.trim(),
                            curr,
                            archived,
                            include,
                            old?.currencyLocked ?: false,
                            group,
                            type.trim(),
                            order.toInt(),
                        )
                    model.saveAccount(candidate, old, if (old == null) curr.parseMinor(opening) else null)
                } catch (
                    _: IllegalArgumentException,
                ) {
                    model.error(R.string.correct_fields)
                } catch (_: ArithmeticException) {
                    model.error(R.string.amount_error)
                }
            }
        }, enabled = !state.saving, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (state.saving) R.string.saving else R.string.save))
        }
        if (old != null && !old.archived) TextButton(onClick = { adjust(old.id) }) { Text(stringResource(R.string.adjust_balance)) }
    }
}

@Composable internal fun AccountsPanel(
    state: BookState,
    edit: (String) -> Unit,
    add: () -> Unit,
    groups: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.accounts), style = MaterialTheme.typography.headlineSmall) }
        item {
            TextButton(onClick = add) { Text(stringResource(R.string.add_account)) }
            TextButton(onClick = groups) { Text(stringResource(R.string.groups)) }
        }
        items(state.accounts, key = { it.account.id }) { row ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable { edit(row.account.id) }
                    .padding(8.dp),
            ) {
                Text(row.account.name, style = MaterialTheme.typography.titleMedium)
                Text("${row.account.currency.code} ${row.account.currency.formatMinor(row.balanceMinor)}")
                val affected = state.affectedIncomplete[row.account.id] ?: 0
                if (affected >
                    0
                ) {
                    Text(stringResource(R.string.incomplete_account_count, affected), style = MaterialTheme.typography.bodySmall)
                }
                state.groups.find { it.id == row.account.groupId }?.let { Text(it.name) }
                if (row.account.archived) Text(stringResource(R.string.archived))
            }
            HorizontalDivider()
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable internal fun GroupsPanel(
    state: BookState,
    edit: (String?) -> Unit,
) {
    Form(R.string.groups, state) {
        TextButton(onClick = { edit(null) }) { Text(stringResource(R.string.new_group)) }
        state.groups.forEach { group -> TextButton(onClick = { edit(group.id) }) { Text(group.name) } }
    }
}

@Composable internal fun GroupForm(
    id: String?,
    state: BookState,
    model: AccountStateOwner,
) {
    val old = remember(id) { state.groups.find { it.id == id } }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(old?.name ?: "") }
    var order by rememberSaveable(id) { mutableStateOf((old?.position ?: 0).toString()) }
    Form(R.string.groups, state) {
        Field(R.string.group_name, name, { name = it })
        Field(R.string.position, order, { order = it }, number = true)
        Button(onClick = {
            order.toIntOrNull()?.let { model.saveGroup(AccountGroup(old?.id ?: newId, name.trim(), it), old) }
                ?: model.error(R.string.correct_fields)
        }, enabled = !state.saving) { Text(stringResource(R.string.save)) }
    }
}

@Composable internal fun AdjustmentForm(
    id: String,
    state: BookState,
    model: AccountStateOwner,
) {
    val account = state.accounts.find { it.account.id == id }?.account ?: return
    var amount by rememberSaveable(id) { mutableStateOf("") }
    var note by rememberSaveable(id) { mutableStateOf("") }
    Form(R.string.adjust_balance, state) {
        Text(account.name, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.adjustment_hint))
        Field(R.string.adjustment_amount, amount, { amount = it }, number = true)
        Field(R.string.notes, note, { note = it })
        Button(onClick = {
            try {
                model.adjust(id, account.currency.parseMinor(amount), note)
            } catch (
                _: IllegalArgumentException,
            ) {
                model.error(R.string.amount_error)
            } catch (_: ArithmeticException) {
                model.error(R.string.amount_error)
            }
        }, enabled = !state.saving) { Text(stringResource(R.string.save)) }
    }
}
