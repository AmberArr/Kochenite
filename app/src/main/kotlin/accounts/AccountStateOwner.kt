package moe.frost.kochenite.accounts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountBalance
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.domain.ledger.AccountRepository
import moe.frost.kochenite.domain.ledger.BalanceAdjustment
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.failure

internal data class AccountState(
    val accounts: List<AccountBalance> = emptyList(),
    val groups: List<AccountGroup> = emptyList(),
    val saving: Boolean = false,
    val error: Int? = null,
)

internal class AccountStateOwner(
    private val scope: CoroutineScope,
    private val repository: () -> AccountRepository?,
    private val otherMutationRunning: () -> Boolean,
    private val origin: () -> String?,
    /** Receives the originating route, or null to stay on the current screen, and whether a ledger entry was posted. */
    private val onChanged: suspend (String?, Boolean) -> Unit,
) {
    private val mutable = MutableStateFlow(AccountState())
    val state = mutable.asStateFlow()

    fun replace(
        accounts: List<AccountBalance>,
        groups: List<AccountGroup>,
    ) {
        mutable.value = mutable.value.copy(accounts = accounts, groups = groups)
    }

    fun error(resource: Int) {
        mutable.value = mutable.value.copy(error = resource)
    }

    fun clearError() {
        mutable.value = mutable.value.copy(error = null)
    }

    private fun mutate(
        postsEntry: Boolean,
        closeOnSuccess: Boolean = true,
        block: suspend (AccountRepository) -> LedgerResult<*>,
    ) {
        if (mutable.value.saving || otherMutationRunning()) return
        val accounts = repository() ?: return
        val route = origin()
        mutable.value = mutable.value.copy(saving = true, error = null)
        scope.launch {
            try {
                val result = block(accounts)
                if (result is LedgerResult.Success) {
                    onChanged(route.takeIf { closeOnSuccess }, postsEntry)
                } else {
                    mutable.value = mutable.value.copy(error = failure(result))
                }
            } finally {
                mutable.value = mutable.value.copy(saving = false)
            }
        }
    }

    /** Creates an account with [opening], or updates one and posts [adjustment] as its balance difference. */
    fun saveAccount(
        account: Account,
        old: Account?,
        opening: Long?,
        adjustment: BalanceAdjustment? = null,
    ) = mutate(postsEntry = opening != null || adjustment != null) {
        if (old != null && adjustment != null) it.saveWithAdjustment(account, old, adjustment) else it.save(account, old, opening)
    }

    /** Group sheets close themselves, so the screen underneath stays. */
    fun saveGroup(
        group: AccountGroup,
        old: AccountGroup?,
    ) = mutate(postsEntry = false, closeOnSuccess = false) { it.saveGroup(group, old) }

    fun reorder(
        groupIds: List<String>,
        accountIds: List<String>,
    ) = mutate(postsEntry = false, closeOnSuccess = false) { it.reorder(groupIds, accountIds) }
}
