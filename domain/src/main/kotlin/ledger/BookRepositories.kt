package moe.frost.kochenite.domain.ledger

import java.time.LocalDate
import java.time.LocalTime

data class AccountGroup(
    val id: String,
    val name: String,
    val position: Int = 0,
)

data class AccountBalance(
    val account: Account,
    val balanceMinor: Long,
)

data class BookSettings(
    val displayCurrency: BookCurrency,
    val setupComplete: Boolean = false,
)

interface AccountRepository {
    suspend fun list(): LedgerResult<List<AccountBalance>>

    suspend fun groups(): LedgerResult<List<AccountGroup>>

    suspend fun save(
        account: Account,
        expected: Account? = null,
        openingMinor: Long? = null,
    ): LedgerResult<Account>

    suspend fun saveGroup(
        group: AccountGroup,
        expected: AccountGroup? = null,
    ): LedgerResult<AccountGroup>

    suspend fun adjust(
        accountId: String,
        deltaMinor: Long,
        date: LocalDate,
        time: LocalTime,
        note: String,
    ): LedgerResult<SavedEntry>
}

interface CategoryRepository {
    suspend fun list(): LedgerResult<List<Category>>

    suspend fun save(
        category: Category,
        expected: Category? = null,
    ): LedgerResult<Category>
}

interface SettingsRepository {
    suspend fun get(): LedgerResult<BookSettings?>

    suspend fun setDisplayCurrency(currency: BookCurrency): LedgerResult<BookSettings>

    suspend fun finishSetup(
        account: Account,
        openingMinor: Long,
        displayCurrency: BookCurrency,
        starterCategories: Boolean,
    ): LedgerResult<Unit>
}
