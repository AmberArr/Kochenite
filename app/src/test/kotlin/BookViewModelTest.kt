package moe.frost.kochenite

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.frost.kochenite.domain.editor.IncompleteRecord
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountBalance
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.domain.ledger.AccountRepository
import moe.frost.kochenite.domain.ledger.AllocationSelection
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.BookServices
import moe.frost.kochenite.domain.ledger.BookSettings
import moe.frost.kochenite.domain.ledger.BulkCategoryRepository
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryRepository
import moe.frost.kochenite.domain.ledger.CurrencyTotals
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.EntryTemplate
import moe.frost.kochenite.domain.ledger.LedgerCursor
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerPage
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.domain.ledger.SettingsRepository
import moe.frost.kochenite.domain.ledger.Tag
import moe.frost.kochenite.domain.ledger.TagRepository
import moe.frost.kochenite.domain.ledger.TemplateRepository
import moe.frost.kochenite.transactions.FakeLedger
import moe.frost.kochenite.transactions.fixtureEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModelTest {
    private val main = StandardTestDispatcher()

    @Before fun setMain() = Dispatchers.setMain(main)

    @After fun resetMain() = Dispatchers.resetMain()

    /** Records each book query so tests can assert which parts a mutation reloads. */
    private class RecordingBook(
        val calls: MutableList<String> = mutableListOf(),
    ) {
        var searchGate: CompletableDeferred<Unit>? = null
        var entries = listOf(fixtureEntry("a"))
        val account = Account("cash", "Cash", BookCurrency("USD", 2))

        val ledger =
            object : FakeLedger() {
                override suspend fun search(
                    filter: LedgerFilter,
                    after: LedgerCursor?,
                    limit: Int,
                ): LedgerResult<LedgerPage> {
                    calls += "search"
                    searchGate?.await()
                    return LedgerResult.Success(LedgerPage(entries, null))
                }

                override suspend fun incompleteRecords(): LedgerResult<List<IncompleteRecord>> {
                    calls += "incomplete"
                    return LedgerResult.Success(emptyList())
                }

                override suspend fun totals(
                    start: LocalDate,
                    end: LocalDate,
                ): LedgerResult<List<CurrencyTotals>> {
                    calls += "totals"
                    return LedgerResult.Success(emptyList())
                }

                override suspend fun trash(
                    entryId: String,
                    expectedToken: String,
                ): LedgerResult<SavedEntry> {
                    val trashed = fixtureEntry(entryId).copy(status = EntryStatus.TRASH)
                    return LedgerResult.Success(trashed)
                }
            }

        val services =
            BookServices(
                object : AccountRepository {
                    override suspend fun list(): LedgerResult<List<AccountBalance>> {
                        calls += "accounts"
                        return LedgerResult.Success(listOf(AccountBalance(account, 0)))
                    }

                    override suspend fun groups(): LedgerResult<List<AccountGroup>> {
                        calls += "groups"
                        return LedgerResult.Success(emptyList())
                    }

                    override suspend fun save(
                        account: Account,
                        expected: Account?,
                        openingMinor: Long?,
                    ) = LedgerResult.Success(account)

                    override suspend fun saveGroup(
                        group: AccountGroup,
                        expected: AccountGroup?,
                    ) = LedgerResult.Success(group)

                    override suspend fun adjust(
                        accountId: String,
                        deltaMinor: Long,
                        date: LocalDate,
                        time: LocalTime,
                        note: String,
                    ): LedgerResult<SavedEntry> = LedgerResult.Success(fixtureEntry("adjustment"))
                },
                object : CategoryRepository {
                    override suspend fun list(): LedgerResult<List<Category>> {
                        calls += "categories"
                        return LedgerResult.Success(emptyList())
                    }

                    override suspend fun save(
                        category: Category,
                        expected: Category?,
                    ) = LedgerResult.Success(category)
                },
                object : SettingsRepository {
                    override suspend fun get(): LedgerResult<BookSettings?> {
                        calls += "settings"
                        return LedgerResult.Success(BookSettings(BookCurrency("USD", 2), setupComplete = true))
                    }

                    override suspend fun setDisplayCurrency(currency: BookCurrency) = LedgerResult.Success(BookSettings(currency, true))

                    override suspend fun finishSetup(
                        account: Account,
                        openingMinor: Long,
                        displayCurrency: BookCurrency,
                        starterCategories: Boolean,
                    ) = LedgerResult.Success(Unit)
                },
                object : TagRepository {
                    override suspend fun list(): LedgerResult<List<Tag>> {
                        calls += "tags"
                        return LedgerResult.Success(emptyList())
                    }

                    override suspend fun save(
                        tag: Tag,
                        expected: Tag?,
                    ) = LedgerResult.Success(tag)
                },
                ledger,
                ledger,
                object : TemplateRepository {
                    override suspend fun templates(): LedgerResult<List<EntryTemplate>> {
                        calls += "templates"
                        return LedgerResult.Success(emptyList())
                    }

                    override suspend fun saveTemplate(template: EntryTemplate) = LedgerResult.Success(template)

                    override suspend fun deleteTemplate(id: String) = LedgerResult.Success(Unit)
                },
                object : BulkCategoryRepository {
                    override suspend fun bulkCategorize(
                        selections: List<AllocationSelection>,
                        categoryId: String?,
                    ) = LedgerResult.Success(selections.size)
                },
            )
    }

    private fun TestScope.loadedModel(book: RecordingBook): BookViewModel {
        val model = BookViewModel { LedgerResult.Success(book.services) }
        advanceUntilIdle()
        assertTrue(model.state.value.storageReady)
        book.calls.clear()
        return model
    }

    @Test fun initialLoadQueriesEveryPart() =
        runTest(main) {
            val book = RecordingBook()
            BookViewModel { LedgerResult.Success(book.services) }
            advanceUntilIdle()
            assertEquals(
                setOf("accounts", "groups", "categories", "tags", "templates", "settings", "incomplete", "totals", "search"),
                book.calls.toSet(),
            )
        }

    @Test fun savingATagReloadsOnlyTags() =
        runTest(main) {
            val book = RecordingBook()
            val model = loadedModel(book)
            model.saveTag(Tag("groceries", "Groceries"), null)
            advanceUntilIdle()
            assertEquals(listOf("tags"), book.calls)
        }

    @Test fun renamingAnAccountDoesNotReloadTotalsOrTheLedger() =
        runTest(main) {
            val book = RecordingBook()
            val model = loadedModel(book)
            model.accountManagement.saveAccount(book.account.copy(name = "Wallet"), book.account, null)
            advanceUntilIdle()
            assertEquals(setOf("accounts", "groups"), book.calls.toSet())
        }

    @Test fun adjustingAnAccountReloadsBalancesTotalsAndLedger() =
        runTest(main) {
            val book = RecordingBook()
            val model = loadedModel(book)
            model.accountManagement.adjust(book.account.id, 100, "")
            advanceUntilIdle()
            assertEquals(setOf("accounts", "groups", "totals", "search"), book.calls.toSet())
        }

    @Test fun trashingKeepsLoadedRowsVisibleUntilTheLedgerRefreshCompletes() =
        runTest(main) {
            val book = RecordingBook()
            val model = loadedModel(book)
            val gate = CompletableDeferred<Unit>()
            book.searchGate = gate
            val entry = book.entries.single()
            book.entries = emptyList()
            model.trashEntry(entry)
            runCurrent()
            assertEquals(setOf("accounts", "groups", "totals", "search"), book.calls.toSet())
            assertEquals(
                listOf("a"),
                model.transactionList.state.value.entries
                    .map { it.id },
            )
            assertTrue(model.transactionList.state.value.loading)
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(
                model.transactionList.state.value.entries
                    .isEmpty(),
            )
        }
}
