package moe.frost.kochenite.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.ui.components.AmountInput
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.ContentMaxWidth
import moe.frost.kochenite.ui.components.CurrencySheet
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SwitchRow
import moe.frost.kochenite.ui.components.currency
import moe.frost.kochenite.ui.components.invalidAmount
import java.util.Currency
import java.util.Locale
import java.util.UUID

/** The device locale's currency, when it is one a book can hold. */
internal fun defaultCurrencyCode(): String =
    runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()?.takeIf { currency(it) != null } ?: "USD"

@Composable
internal fun SetupScreen(
    state: BookState,
    model: BookViewModel,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf(defaultCurrencyCode()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var negative by rememberSaveable { mutableStateOf(false) }
    var starter by rememberSaveable { mutableStateOf(true) }
    var choosingCurrency by rememberSaveable { mutableStateOf(false) }
    val id = rememberSaveable { UUID.randomUUID().toString() }
    val accountCurrency = currency(code)
    val amountInvalid = invalidAmount(amount, accountCurrency)
    Scaffold(containerColor = MaterialTheme.colorScheme.surface, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().padding(horizontal = 24.dp)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 32.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier.size(64.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(R.drawable.ic_wallet, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(
                        stringResource(R.string.setup_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.setup_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
                    Field(R.string.account_name, name, { name = it })
                    AmountInput(
                        stringResource(R.string.opening_balance),
                        accountCurrency,
                        amount,
                        { amount = it },
                        negative = negative,
                        onToggleSign = { negative = !negative },
                        enabled = !state.saving,
                        onCurrencyClick = { choosingCurrency = true },
                        error = if (amountInvalid) stringResource(R.string.amount_error) else null,
                    )
                    SwitchRow(
                        stringResource(R.string.starter_categories),
                        starter,
                        supporting = stringResource(R.string.starter_categories_hint),
                    ) { starter = it }
                }
                Button(
                    onClick = {
                        val opening =
                            if (amount.isBlank()) {
                                0L
                            } else {
                                runCatching { accountCurrency?.parseMinor(if (negative) "-$amount" else amount) }.getOrNull()
                            }
                        when {
                            accountCurrency == null -> model.error(R.string.currency_error)
                            opening == null -> model.error(R.string.amount_error)
                            name.isBlank() -> model.error(R.string.correct_fields)
                            else -> model.setup(Account(id, name.trim(), accountCurrency), opening, starter)
                        }
                    },
                    enabled = !state.saving,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .heightIn(min = 48.dp)
                            .testTag("create-book"),
                ) {
                    Text(stringResource(if (state.saving) R.string.saving else R.string.create_book))
                }
            }
        }
    }
    if (choosingCurrency) {
        CurrencySheet(accountCurrency, emptyList(), { choosingCurrency = false }) {
            code = it.code
            choosingCurrency = false
        }
    }
}
