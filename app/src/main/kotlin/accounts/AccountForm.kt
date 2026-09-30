package moe.frost.kochenite.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.BalanceAdjustment
import moe.frost.kochenite.transactions.AccountTypes
import moe.frost.kochenite.ui.components.AmountInput
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.AppTopBar
import moe.frost.kochenite.ui.components.ContentMaxWidth
import moe.frost.kochenite.ui.components.CurrencySheet
import moe.frost.kochenite.ui.components.DropdownField
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.GroupCard
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SignDisplay
import moe.frost.kochenite.ui.components.SwitchRow
import moe.frost.kochenite.ui.components.currency
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.invalidAmount
import java.util.UUID

/**
 * New accounts take an opening balance and a currency from the amount control. Existing accounts show
 * their balance in the same control; changing it saves the difference as one balance adjustment.
 */
@Composable
internal fun AccountFormScreen(
    id: String?,
    state: BookState,
    model: AccountStateOwner,
    onBack: (() -> Unit)?,
) {
    val row = remember(id) { state.accounts.find { it.account.id == id } }
    val old = row?.account
    val balance = row?.balanceMinor ?: 0L
    val newId = rememberSaveable(id) { UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(old?.name ?: "") }
    var code by rememberSaveable(id) { mutableStateOf(old?.currency?.code ?: state.settings?.displayCurrency?.code ?: "USD") }
    var negative by rememberSaveable(id) { mutableStateOf(balance < 0) }
    var amount by
        rememberSaveable(id) {
            mutableStateOf(old?.currency?.formatMinor(balance)?.removePrefix("-") ?: "")
        }
    var note by rememberSaveable(id) { mutableStateOf("") }
    var type by rememberSaveable(id) { mutableStateOf(old?.type ?: "") }
    var group by rememberSaveable(id) { mutableStateOf(old?.groupId) }
    var include by rememberSaveable(id) { mutableStateOf(old?.includeInNetWorth ?: true) }
    var archived by rememberSaveable(id) { mutableStateOf(old?.archived ?: false) }
    var choosingCurrency by rememberSaveable(id) { mutableStateOf(false) }
    val accountCurrency = currency(code)
    val amountInvalid = invalidAmount(amount, accountCurrency)
    val entered =
        if (amount.isBlank()) 0L else runCatching { accountCurrency?.parseMinor(if (negative) "-$amount" else amount) }.getOrNull()
    // The currency can change only before the first posting, when the balance is still zero.
    val delta =
        if (old != null && entered != null &&
            accountCurrency == old.currency
        ) {
            runCatching { Math.subtractExact(entered, balance) }.getOrNull()
        } else {
            null
        }
    val balanceChanged = old != null && delta != null && delta != 0L

    fun save() {
        val chosen = accountCurrency
        when {
            chosen == null -> {
                model.error(R.string.currency_error)
            }

            entered == null || (old != null && chosen == old.currency && delta == null) -> {
                model.error(R.string.amount_error)
            }

            // A balance difference is posted in the account's currency, so the two cannot change in one save.
            old != null && chosen != old.currency && entered != 0L -> {
                model.error(R.string.change_currency_first)
            }

            name.isBlank() -> {
                model.error(R.string.correct_fields)
            }

            else -> {
                val candidate =
                    Account(
                        old?.id ?: newId,
                        name.trim(),
                        chosen,
                        archived,
                        include,
                        old?.currencyLocked ?: false,
                        group,
                        type.trim(),
                        old?.position ?: ((state.accounts.maxOfOrNull { it.account.position } ?: -1) + 1),
                    )
                if (old == null) {
                    model.saveAccount(candidate, null, entered)
                } else {
                    model.saveAccount(
                        candidate,
                        old,
                        null,
                        if (balanceChanged) BalanceAdjustment(balance, entered, note.trim()) else null,
                    )
                }
            }
        }
    }
    AppScaffold(
        topBar = {
            AppTopBar(
                stringResource(
                    if (old ==
                        null
                    ) {
                        R.string.new_account
                    } else {
                        R.string.edit_account
                    },
                ),
                onBack = onBack,
                close = true,
                backEnabled = !state.saving,
            ) {
                TextButton(onClick = ::save, enabled = !state.saving, modifier = Modifier.testTag("save-account")) {
                    Text(stringResource(if (state.saving) R.string.saving else R.string.save))
                }
            }
        },
        maxWidth = if (onBack == null) ContentMaxWidth * 2 else ContentMaxWidth,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            val balanceInput: @Composable () -> Unit = {
                AmountInput(
                    stringResource(if (old == null) R.string.opening_balance else R.string.balance),
                    accountCurrency,
                    amount,
                    { amount = it },
                    negative = negative,
                    onToggleSign = { negative = !negative },
                    // An archived account takes no postings, so its balance cannot be adjusted.
                    enabled = !state.saving && old?.archived != true,
                    onCurrencyClick = if (old?.currencyLocked == true) null else ({ choosingCurrency = true }),
                    error = if (amountInvalid) stringResource(R.string.amount_error) else null,
                )
            }
            val nameField: @Composable () -> Unit = { Field(R.string.account_name, name, { name = it }, enabled = !state.saving) }
            if (old == null) {
                nameField()
                balanceInput()
            } else {
                balanceInput()
                if (balanceChanged) {
                    Notice(
                        stringResource(R.string.balance_adjustment_notice, old.currency.display(delta, SignDisplay.ALWAYS)),
                        Modifier.testTag("balance-adjustment-notice"),
                    )
                    Field(R.string.adjustment_note, note, { note = it }, enabled = !state.saving, clearable = true)
                }
                nameField()
            }
            DropdownField(
                stringResource(R.string.account_type),
                type.ifBlank { stringResource(R.string.no_type) },
                listOf(null to stringResource(R.string.no_type)) +
                    (AccountTypes + listOfNotNull(type.takeIf { it.isNotBlank() })).distinct().map { it to it },
                supporting = stringResource(R.string.account_type_hint),
                enabled = !state.saving,
                tag = "account-type",
            ) { type = it.orEmpty() }
            DropdownField(
                stringResource(R.string.group),
                state.groups.find { it.id == group }?.name ?: stringResource(R.string.no_group),
                listOf(null to stringResource(R.string.no_group)) + state.groups.map { it.id to it.name },
                enabled = !state.saving,
                tag = "account-group",
            ) { group = it }
            GroupCard {
                SwitchRow(
                    stringResource(R.string.include_net_worth),
                    include,
                    supporting = if (old == null) stringResource(R.string.include_net_worth_hint) else null,
                    enabled = !state.saving,
                    modifier = Modifier.testTag("account-include"),
                ) { include = it }
                if (old != null) {
                    SwitchRow(
                        stringResource(R.string.archived),
                        archived,
                        supporting = stringResource(R.string.archived_account_hint),
                        enabled = !state.saving,
                        modifier = Modifier.testTag("account-archived"),
                    ) { archived = it }
                }
            }
        }
    }
    if (choosingCurrency) {
        CurrencySheet(accountCurrency, state.accounts.map { it.account.currency }, { choosingCurrency = false }) {
            code = it.code
            choosingCurrency = false
        }
    }
}
