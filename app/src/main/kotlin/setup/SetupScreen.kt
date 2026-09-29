package moe.frost.kochenite.setup

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.Form
import moe.frost.kochenite.ui.components.Toggle
import moe.frost.kochenite.ui.components.currency
import java.util.UUID

@Composable internal fun SetupForm(
    state: BookState,
    model: BookViewModel,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var accountCode by rememberSaveable { mutableStateOf("") }
    var displayCode by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("0") }
    var starter by rememberSaveable { mutableStateOf(true) }
    val id = rememberSaveable { UUID.randomUUID().toString() }
    Form(R.string.setup_title, state) {
        Text(stringResource(R.string.setup_intro))
        Field(R.string.account_name, name, { name = it })
        Field(R.string.account_currency, accountCode, { accountCode = it })
        Text(stringResource(R.string.currency_hint), style = MaterialTheme.typography.bodySmall)
        Field(R.string.opening_balance, amount, { amount = it }, number = true)
        Field(R.string.display_currency, displayCode, { displayCode = it })
        Toggle(R.string.starter_categories, starter) { starter = it }
        Button(onClick = {
            val accountCurrency = currency(accountCode)
            val display = currency(displayCode)
            if (accountCurrency == null || display == null) {
                model.error(R.string.currency_error)
            } else {
                try {
                    model.setup(Account(id, name.trim(), accountCurrency), accountCurrency.parseMinor(amount), display, starter)
                } catch (
                    _: IllegalArgumentException,
                ) {
                    model.error(R.string.amount_error)
                } catch (_: ArithmeticException) {
                    model.error(R.string.amount_error)
                }
            }
        }, enabled = !state.saving, modifier = Modifier.heightIn(min = 48.dp).testTag("create-book")) {
            Text(stringResource(if (state.saving) R.string.saving else R.string.create_book))
        }
        if (state.error == R.string.unavailable ||
            state.error == R.string.recovery
        ) {
            TextButton(onClick = model::refresh) { Text(stringResource(R.string.retry)) }
        }
    }
}
