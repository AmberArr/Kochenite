package moe.frost.kochenite.persistence.ledger

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.AllocationSelection
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.Draft
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.EntryTemplate
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.Money
import moe.frost.kochenite.domain.ledger.ReceiptLine
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.domain.ledger.Tag
import moe.frost.kochenite.persistence.PreservingSQLiteFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class FinancialStorageDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "financial-test-${UUID.randomUUID()}.db"
    private val usd = BookCurrency("USD", 2)
    private val today = LocalDate.of(2026, 9, 22)
    private var db: LedgerDatabase? = null

    @get:Rule val migration =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            LedgerDatabase::class.java,
            emptyList(),
            PreservingSQLiteFactory(),
        )

    @After fun cleanup() {
        db?.close()
        context.deleteDatabase(name)
    }

    private fun open(afterWrite: () -> Unit = {}): RoomLedger {
        db?.close()
        val opened = ledgerDatabase(context, name)
        db = opened
        return RoomLedger(opened, { today }, afterWrite)
    }

    private fun <T> success(result: LedgerResult<T>): T {
        assertTrue("Expected successful operation", result is LedgerResult.Success)
        return (result as LedgerResult.Success).value
    }

    private suspend fun seed(ledger: RoomLedger) {
        success(ledger.createAccount(Account("cash", "Cash", usd)))
        success(ledger.createAccount(Account("card", "Card", usd)))
        success(ledger.createAccount(Account("yen", "Yen", BookCurrency("JPY", 0))))
    }

    private fun expense(amount: Long = 1234) =
        EntryInput(
            EntryKind.EXPENSE,
            today,
            LocalTime.NOON,
            "cash",
            -amount,
            allocations = listOf(Allocation("a", amount)),
            merchant = "Synthetic 合成",
            note = "日本語 • café",
        )

    private suspend fun draft(
        ledger: RoomLedger,
        input: EntryInput = expense(),
        incomplete: Boolean = false,
    ): Draft =
        success(
            ledger.saveDraft(Draft(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", input, incomplete = incomplete), null),
        )

    private suspend fun post(
        ledger: RoomLedger,
        input: EntryInput,
    ): SavedEntry {
        val saved = draft(ledger, input)
        return success(ledger.confirm(saved.id, saved.token))
    }

    @Test fun draftListingRawInputAndDiscardAreDurableAndTokenChecked() =
        runBlocking {
            var ledger = open()
            seed(ledger)
            val first = draft(ledger).copy(amountText = "12.", dateText = "2026-09-", timeText = "12:")
            val saved = success(ledger.saveDraft(first, first.token))
            val second = draft(ledger)
            ledger = open()
            assertEquals(listOf(second, saved), success(ledger.drafts()))
            assertEquals(LedgerResult.Conflict, ledger.discardDraft(saved.id, first.token))
            assertEquals(saved, success(ledger.draft(saved.id)))
            success(ledger.discardDraft(saved.id, saved.token))
            assertEquals(LedgerResult.Missing, ledger.draft(saved.id))
            assertEquals(listOf(second), success(ledger.drafts()))
            assertEquals(LedgerResult.Invalid(InvalidReason.STATE), ledger.drafts(0))
        }

    @Test fun transferAndMerchantEditorTextCannotInventOrHideActualAmounts() =
        runBlocking {
            var ledger = open()
            seed(ledger)
            val input =
                expense(1000).copy(
                    kind = EntryKind.TRANSFER,
                    destinationAccountId = "yen",
                    amountMinor = 1000,
                    receivedMinor = 1500,
                    allocations = emptyList(),
                    merchantAmount = Money(1550, BookCurrency("JPY", 0)),
                )
            val raw =
                Draft(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "",
                    input,
                    amountText = "10.00",
                    receivedText = "1500",
                    merchantAmountText = "1550",
                    merchantCurrencyText = "JPY",
                )
            var saved = success(ledger.saveDraft(raw, null))
            ledger = open()
            assertEquals(saved, success(ledger.draft(saved.id)))
            success(ledger.confirm(saved.id, saved.token))
            assertEquals(-1000L, success(ledger.balance("cash")))
            assertEquals(1500L, success(ledger.balance("yen")))
            assertTrue(success(ledger.totals(today, today)).isEmpty())

            saved =
                success(
                    ledger.saveDraft(
                        raw.copy(id = UUID.randomUUID().toString(), entryId = UUID.randomUUID().toString(), amountText = "11.00"),
                        null,
                    ),
                )
            assertEquals(LedgerResult.Invalid(InvalidReason.AMOUNT), ledger.confirm(saved.id, saved.token))
            assertEquals(saved, success(ledger.draft(saved.id)))
            assertEquals(-1000L, success(ledger.balance("cash")))
            val badDate = success(ledger.saveDraft(saved.copy(amountText = "10.00", dateText = "2026-09-"), saved.token))
            assertEquals(LedgerResult.Invalid(InvalidReason.DATE), ledger.confirm(badDate.id, badDate.token))
            assertEquals(badDate, success(ledger.draft(badDate.id)))
        }

    @Test fun signedExpenseCreditsUseTheirOwnPeriodAndRelatedRecords() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val purchaseDate = today.withDayOfMonth(1)
            val purchase = post(ledger, expense(1000).copy(date = purchaseDate))
            val first = post(ledger, expense(300).copy(amountMinor = 300, accountId = "card", relatedEntryIds = listOf(purchase.id)))
            val second = post(ledger, expense(100).copy(amountMinor = 100, accountId = "card", relatedEntryIds = listOf(purchase.id)))
            val standalone = post(ledger, expense(200).copy(amountMinor = 200))
            assertEquals(purchase.id, first.input.relatedEntryIds.single())
            assertEquals(purchase.id, second.input.relatedEntryIds.single())
            assertEquals(emptyList<String>(), standalone.input.relatedEntryIds)
            val edit = success(ledger.beginEdit(first.id))
            success(ledger.confirm(edit.id, edit.token))
            assertEquals(listOf(purchase.id), success(ledger.entry(first.id)).input.relatedEntryIds)
            val copied = success(ledger.copyEntry(first.id))
            assertEquals(emptyList<String>(), copied.input.relatedEntryIds)
            assertEquals(-800L, success(ledger.balance("cash")))
            assertEquals(400L, success(ledger.balance("card")))
            assertEquals(1000L, success(ledger.totals(purchaseDate, purchaseDate)).single().spendingMinor)
            assertEquals(-600L, success(ledger.totals(today, today)).single().spendingMinor)
        }

    @Test fun exactReopenDraftConsumptionAndReports() =
        runBlocking {
            var ledger = open()
            seed(ledger)
            val saved = draft(ledger, expense(9007199254740993L))
            ledger = open()
            assertEquals(saved, success(ledger.draft(saved.id)))
            val entry = success(ledger.confirm(saved.id, saved.token))
            assertEquals(LedgerResult.AlreadyConfirmed, ledger.confirm(saved.id, saved.token))
            assertEquals(LedgerResult.Missing, ledger.draft(saved.id))
            ledger = open()
            assertEquals(entry, success(ledger.entry(entry.id)))
            assertEquals(-9007199254740993L, success(ledger.balance("cash")))
            val totals = success(ledger.totals(today, today)).single()
            assertEquals(9007199254740993L, totals.spendingMinor)
            assertEquals(0L, totals.incomeMinor)
            db!!.openHelper.writableDatabase.query("PRAGMA integrity_check").use {
                assertTrue(it.moveToFirst())
                assertEquals("ok", it.getString(0))
            }
        }

    @Test fun staleEditsAndDraftSavesPreserveCurrentRecords() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val original = post(ledger, expense())
            val first = success(ledger.beginEdit(original.id))
            val second = success(ledger.beginEdit(original.id))
            val updated = success(ledger.saveDraft(first.copy(input = expense(1500)), first.token))
            assertEquals(LedgerResult.Conflict, ledger.saveDraft(first.copy(input = expense(2000)), first.token))
            assertEquals(LedgerResult.Conflict, ledger.confirm(first.id, first.token))
            val replacement = success(ledger.confirm(updated.id, updated.token))
            assertEquals(original.id, replacement.id)
            assertEquals(LedgerResult.Conflict, ledger.confirm(second.id, second.token))
            assertEquals(second, success(ledger.draft(second.id)))
            assertEquals(-1500L, success(ledger.balance("cash")))
            assertEquals(1500L, success(ledger.totals(today, today)).single().spendingMinor)
        }

    @Test fun injectedFailureAndCancellationRollBackAllWrites() =
        runBlocking {
            var ledger = open()
            seed(ledger)
            val original = post(ledger, expense())
            val editing = success(ledger.beginEdit(original.id))
            val changed = success(ledger.saveDraft(editing.copy(input = expense(1500)), editing.token))
            ledger = open { throw IOException("Synthetic unavailable storage") }
            assertEquals(LedgerResult.TemporarilyUnavailable, ledger.confirm(changed.id, changed.token))
            assertEquals(original, success(ledger.entry(original.id)))
            assertEquals(changed, success(ledger.draft(changed.id)))
            ledger = open { throw CancellationException("Synthetic interruption") }
            try {
                ledger.confirm(changed.id, changed.token)
                fail("Expected cancellation")
            } catch (_: CancellationException) {
            }
            ledger = open()
            assertEquals(original, success(ledger.entry(original.id)))
            assertEquals(-1234L, success(ledger.balance("cash")))
            assertEquals(changed, success(ledger.draft(changed.id)))
            success(ledger.confirm(changed.id, changed.token))
            assertEquals(-1500L, success(ledger.balance("cash")))
        }

    @Test fun balanceOverflowRollsBackEntryAndCurrencyLock() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            post(ledger, expense().copy(kind = EntryKind.OPENING, amountMinor = Long.MAX_VALUE, allocations = emptyList()))
            val saved =
                draft(
                    ledger,
                    expense(
                        1,
                    ).copy(
                        kind = EntryKind.TRANSFER,
                        amountMinor = 1,
                        accountId = "yen",
                        destinationAccountId = "cash",
                        receivedMinor = 1,
                        allocations = emptyList(),
                    ),
                )
            assertEquals(LedgerResult.Invalid(InvalidReason.OVERFLOW), ledger.confirm(saved.id, saved.token))
            assertEquals(saved, success(ledger.draft(saved.id)))
            assertEquals(Long.MAX_VALUE, success(ledger.balance("cash")))
            assertEquals(0L, success(ledger.balance("yen")))
            assertFalse(success(ledger.accounts()).single { it.id == "yen" }.currencyLocked)
            assertEquals(1, success(ledger.page()).entries.size)
        }

    @Test fun trashRestoreAndPurgeDetachRefundWithoutChangingItsAmount() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val purchase = post(ledger, expense(1500))
            val refund = post(ledger, expense(400).copy(amountMinor = 400, accountId = "card", relatedEntryIds = listOf(purchase.id)))
            assertEquals(1100L, success(ledger.totals(today, today)).single().spendingMinor)
            var trashed = success(ledger.trash(purchase.id, purchase.token))
            assertEquals(0L, success(ledger.balance("cash")))
            assertEquals(-400L, success(ledger.totals(today, today)).single().spendingMinor)
            val restored = success(ledger.restore(purchase.id, trashed.token))
            assertEquals(-1500L, success(ledger.balance("cash")))
            assertEquals(restored, success(ledger.restore(purchase.id, restored.token)))
            val refundEdit = success(ledger.beginEdit(refund.id))
            trashed = success(ledger.trash(purchase.id, restored.token))
            success(ledger.purge(purchase.id, trashed.token))
            assertEquals(LedgerResult.Missing, ledger.entry(purchase.id))
            val surviving = success(ledger.entry(refund.id))
            assertEquals(emptyList<String>(), surviving.input.relatedEntryIds)
            assertEquals(400L, surviving.input.amountMinor)
            assertEquals(400L, success(ledger.balance("card")))
            assertEquals(LedgerResult.Conflict, ledger.confirm(refundEdit.id, refundEdit.token))
            assertTrue(success(ledger.accounts()).single { it.id == "cash" }.currencyLocked)
        }

    @Test fun incompleteAndFutureInputsHaveNoFinancialEffect() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            assertEquals(
                LedgerResult.Invalid(InvalidReason.STATE),
                ledger.saveDraft(Draft("invalid-incomplete", "invalid-entry", "", expense(), incomplete = true), null),
            )
            val incomplete =
                draft(
                    ledger,
                    expense().copy(
                        kind = EntryKind.TRANSFER,
                        amountMinor = 1234,
                        destinationAccountId = "yen",
                        receivedMinor = null,
                        allocations = emptyList(),
                        merchantAmount = Money(1000, BookCurrency("JPY", 0)),
                    ),
                    true,
                )
            assertEquals(listOf(incomplete), success(ledger.incompleteDrafts()))
            assertEquals(LedgerResult.Invalid(InvalidReason.MISSING_AMOUNT), ledger.confirm(incomplete.id, incomplete.token))
            assertEquals(0L, success(ledger.balance("cash")))
            assertEquals(0L, success(ledger.balance("yen")))
            assertTrue(success(ledger.totals(today, today)).isEmpty())
            val completeInput = incomplete.copy(input = incomplete.input.copy(receivedMinor = 1800), incomplete = false)
            val completed = success(ledger.saveDraft(completeInput, incomplete.token))
            success(ledger.confirm(completed.id, completed.token))
            assertTrue(success(ledger.incompleteDrafts()).isEmpty())
            assertEquals(-1234L, success(ledger.balance("cash")))
            assertEquals(1800L, success(ledger.balance("yen")))
            assertTrue(success(ledger.totals(today, today)).isEmpty())
            val future = draft(ledger, expense().copy(date = today.plusDays(1)))
            assertEquals(LedgerResult.Invalid(InvalidReason.FUTURE_DATE), ledger.confirm(future.id, future.token))
            assertEquals(future, success(ledger.draft(future.id)))
        }

    @Test fun boundedKeysetPagesIncludeTiesExactlyOnce() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val expected = (1..7).map { post(ledger, expense(it.toLong())).id }.toSet()
            val first = success(ledger.page(limit = 3))
            val second = success(ledger.page(after = first.next, limit = 3))
            val third = success(ledger.page(after = second.next, limit = 3))
            assertEquals(expected, (first.entries + second.entries + third.entries).map { it.id }.toSet())
            assertEquals(3, first.entries.size)
            assertEquals(3, second.entries.size)
            assertEquals(1, third.entries.size)
            assertEquals(null, third.next)
            assertEquals(LedgerResult.Invalid(InvalidReason.STATE), ledger.page(limit = 201))
            val row = first.entries.first()
            success(ledger.trash(row.id, row.token))
            assertEquals(1, success(ledger.page(EntryStatus.TRASH)).entries.size)
        }

    private fun version1Fixture() {
        migration.createDatabase(name, 1).use { old ->
            old.execSQL("INSERT INTO accounts VALUES ('cash','Synthetic','USD',2,0,1,1)")
            old.execSQL(
                """
                INSERT INTO entries VALUES ('entry','token','draft','POSTED','EXPENSE','2026-09-22','12:00','cash',
                9007199254740993,NULL,NULL,'Synthetic',NULL,NULL,NULL,NULL)
                """.trimIndent(),
            )
            old.execSQL("INSERT INTO postings VALUES ('entry','cash',-9007199254740993)")
            old.execSQL("INSERT INTO allocations VALUES ('entry','a',0,9007199254740993,NULL)")
            val saved = Draft("saved-draft", "next-entry", "saved-token", expense()).row()
            old.execSQL(
                "INSERT INTO drafts VALUES (?,?,?,?,?,?)",
                arrayOf<Any?>(saved.id, saved.entryId, saved.token, null, 0, saved.payload),
            )
        }
    }

    @Test fun version1MigrationPreservesLedgerAndDraftsAcrossReopens() =
        runBlocking {
            version1Fixture()
            migration.runMigrationsAndValidate(name, 2, true, ledgerMigration1To2).close()
            repeat(2) {
                val ledger = open()
                assertEquals(-9007199254740993L, success(ledger.balance("cash")))
                assertEquals("", success(ledger.entry("entry")).input.note)
                assertEquals(expense(), success(ledger.draft("saved-draft")).input)
                assertTrue(success(ledger.accounts()).single().currencyLocked)
            }
        }

    @Test fun version3MigrationAddsReceiptLinesWithoutChangingExistingRecords() =
        runBlocking {
            migration.createDatabase(name, 3).use { old ->
                old.execSQL(
                    "INSERT INTO accounts (id,name,currency,scale,archived,includeInNetWorth,currencyLocked,groupId,type,position) VALUES ('cash','Cash','USD',2,0,1,0,NULL,'',0)",
                )
            }
            migration.runMigrationsAndValidate(name, 4, true, ledgerMigration3To4).close()
            val ledger = open()
            assertEquals("cash", success(ledger.accounts()).single().id)
            assertTrue(success(ledger.page()).entries.isEmpty())
        }

    @Test fun version4MigrationAddsTagsAndTemplatesWithoutChangingBalances() =
        runBlocking {
            migration.createDatabase(name, 4).use { old ->
                old.execSQL(
                    "INSERT INTO accounts (id,name,currency,scale,archived,includeInNetWorth,currencyLocked,groupId,type,position) VALUES ('cash','Cash','USD',2,0,1,0,NULL,'',0)",
                )
            }
            migration.runMigrationsAndValidate(name, 5, true, ledgerMigration4To5).close()
            val ledger = open()
            assertEquals("cash", success(ledger.accounts()).single().id)
            assertEquals(0L, success(ledger.balance("cash")))
            assertTrue(success(ledger.tagRepository.list()).isEmpty())
        }

    @Test fun boundedSearchFindsUnicodeReceiptTextAndExactTagCategoryCurrencyFilters() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            success(ledger.categoryRepository.save(Category("food", "Food", CategoryKind.EXPENSE)))
            success(ledger.categoryRepository.save(Category("fruit", "Fruit", CategoryKind.EXPENSE, parentId = "food")))
            success(ledger.tagRepository.save(Tag("trip", "Trip")))
            val first =
                post(
                    ledger,
                    expense(1200).copy(
                        merchant = "Café 東京",
                        allocations = listOf(Allocation("line", 1200, "fruit")),
                        receiptLines = listOf(ReceiptLine("line", "りんご Apple", 1200)),
                        tagIds = listOf("trip"),
                    ),
                )
            val second = post(ledger, expense(500).copy(merchant = "Other", note = "Unicode résumé"))
            assertEquals(listOf(first.id), success(ledger.search(LedgerFilter(text = "東京"))).entries.map { it.id })
            assertEquals(listOf(first.id), success(ledger.search(LedgerFilter(text = "りんご"))).entries.map { it.id })
            assertEquals(listOf(second.id), success(ledger.search(LedgerFilter(text = "résumé"))).entries.map { it.id })
            assertEquals(listOf(first.id), success(ledger.search(LedgerFilter(categoryId = "food"))).entries.map { it.id })
            assertEquals(listOf(first.id), success(ledger.search(LedgerFilter(tagId = "trip"))).entries.map { it.id })
            assertEquals(
                listOf(
                    first.id,
                ),
                success(
                    ledger.search(LedgerFilter(accountId = "cash", amountCurrency = usd, minimumMinor = 1200, maximumMinor = 1200)),
                ).entries.map {
                    it.id
                },
            )
            assertTrue(success(ledger.search(LedgerFilter(amountCurrency = BookCurrency("JPY", 0), minimumMinor = 1200))).entries.isEmpty())
            val page = success(ledger.search(LedgerFilter(), limit = 1))
            assertEquals(1, page.entries.size)
            assertEquals(1, success(ledger.search(LedgerFilter(), page.next, 1)).entries.size)
            success(ledger.tagRepository.save(Tag("trip", "Journey", archived = true), Tag("trip", "Trip")))
            val editing = success(ledger.beginEdit(first.id))
            success(ledger.confirm(editing.id, editing.token))
            val fresh = draft(ledger, expense(100).copy(tagIds = listOf("trip")))
            assertEquals(LedgerResult.Invalid(InvalidReason.TAG), ledger.confirm(fresh.id, fresh.token))
            val current = success(ledger.entry(first.id))
            success(ledger.trash(first.id, current.token))
            assertEquals(listOf(first.id), success(ledger.search(LedgerFilter(status = EntryStatus.TRASH))).entries.map { it.id })
        }

    @Test fun copyAndTemplateCreateDistinctDraftsThatNeverPostAutomatically() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val source =
                post(
                    ledger,
                    expense(1000).copy(merchant = "Reusable", allocations = listOf(Allocation("food", 600), Allocation("travel", 400))),
                )
            val copied = success(ledger.copyEntry(source.id))
            assertTrue(copied.entryId != source.id)
            assertTrue(
                copied.input.allocations
                    .map { it.id }
                    .none { id -> source.input.allocations.any { it.id == id } },
            )
            assertEquals(source.input.allocations.map { it.amountMinor }, copied.input.allocations.map { it.amountMinor })
            assertEquals(emptyList<String>(), copied.input.relatedEntryIds)
            val template =
                EntryTemplate(
                    "routine",
                    "Routine",
                    source.input.copy(amountMinor = null, allocations = source.input.allocations.map { it.copy(amountMinor = 0) }),
                )
            success(ledger.saveTemplate(template))
            assertEquals(template, success(ledger.templates()).single())
            val applied = success(ledger.applyTemplate("routine"))
            assertTrue(applied.id != copied.id && applied.entryId != source.id)
            assertEquals(null, applied.input.amountMinor)
            assertEquals(LedgerResult.Invalid(InvalidReason.MISSING_AMOUNT), ledger.confirm(applied.id, applied.token))
            assertEquals(-1000L, success(ledger.balance("cash")))
            assertEquals(1, success(ledger.page()).entries.size)
            success(ledger.deleteTemplate("routine"))
            assertTrue(success(ledger.templates()).isEmpty())
        }

    @Test fun bulkCategorizationRejectsStaleRowsAndRollsBackWholeBatch() =
        runBlocking {
            var ledger = open()
            seed(ledger)
            success(ledger.categoryRepository.save(Category("food", "Food", CategoryKind.EXPENSE)))
            val first = post(ledger, expense(600).copy(merchant = "First"))
            val second = post(ledger, expense(400).copy(merchant = "Second"))
            val selections =
                listOf(
                    AllocationSelection(first.id, "a", first.token),
                    AllocationSelection(second.id, "a", second.token),
                )
            assertEquals(
                LedgerResult.Conflict,
                ledger.bulkCategorize(
                    selections.mapIndexed { index, row ->
                        if (index == 1) row.copy(expectedToken = "stale") else row
                    },
                    "food",
                ),
            )
            assertEquals(
                null,
                success(ledger.entry(first.id))
                    .input.allocations
                    .single()
                    .categoryId,
            )
            ledger = open { throw IOException("Synthetic interrupted bulk write") }
            assertEquals(LedgerResult.TemporarilyUnavailable, ledger.bulkCategorize(selections, "food"))
            ledger = open()
            assertEquals(
                null,
                success(ledger.entry(first.id))
                    .input.allocations
                    .single()
                    .categoryId,
            )
            assertEquals(2, success(ledger.bulkCategorize(selections, "food")))
            assertEquals(
                "food",
                success(ledger.entry(first.id))
                    .input.allocations
                    .single()
                    .categoryId,
            )
            assertEquals(
                "food",
                success(ledger.entry(second.id))
                    .input.allocations
                    .single()
                    .categoryId,
            )
            assertEquals(-1000L, success(ledger.balance("cash")))
            assertEquals(1000L, success(ledger.totals(today, today)).single().spendingMinor)
            assertEquals(LedgerResult.Conflict, ledger.bulkCategorize(selections, null))
        }

    @Test fun itemizedForeignReceiptsPersistAndEditsReplaceLinesAtomically() =
        runBlocking {
            var ledger = open()
            seed(ledger)
            val original =
                expense(1001).copy(
                    merchantAmount = Money(300, BookCurrency("JPY", 0)),
                    allocations = listOf(Allocation("a", 334), Allocation("b", 334), Allocation("c", 333)),
                    receiptLines = listOf(ReceiptLine("a", "One", 100), ReceiptLine("b", "Two", 100), ReceiptLine("c", "Three", 100)),
                )
            val posted = post(ledger, original)
            ledger = open()
            assertEquals(original, success(ledger.entry(posted.id)).input)
            assertEquals(-1001L, success(ledger.balance("cash")))
            val editing = success(ledger.beginEdit(posted.id))
            val malformed =
                success(
                    ledger.saveDraft(editing.copy(input = original.copy(receiptLines = original.receiptLines.dropLast(1))), editing.token),
                )
            assertEquals(LedgerResult.Invalid(InvalidReason.ALLOCATIONS), ledger.confirm(malformed.id, malformed.token))
            assertEquals(original, success(ledger.entry(posted.id)).input)
            assertEquals(-1001L, success(ledger.balance("cash")))
            val corrected =
                original.copy(
                    allocations = listOf(Allocation("a", 500), Allocation("c", 501)),
                    receiptLines = listOf(ReceiptLine("a", "First", 150), ReceiptLine("c", "Last", 150)),
                )
            val ready = success(ledger.saveDraft(malformed.copy(input = corrected), malformed.token))
            success(ledger.confirm(ready.id, ready.token))
            ledger = open()
            assertEquals(corrected, success(ledger.entry(posted.id)).input)
            assertEquals(-1001L, success(ledger.balance("cash")))
            assertEquals(1001L, success(ledger.totals(today, today)).single().spendingMinor)
        }

    @Test fun interruptedMigrationPreservesOldRowsAndRetries() =
        runBlocking {
            version1Fixture()
            val broken =
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        ledgerMigration1To2.migrate(db)
                        throw IOException("Synthetic migration interruption")
                    }
                }
            val candidate =
                Room
                    .databaseBuilder(
                        context,
                        LedgerDatabase::class.java,
                        name,
                    ).openHelperFactory(PreservingSQLiteFactory())
                    .addMigrations(broken, ledgerMigration2To3)
                    .build()
            try {
                candidate.openHelper.writableDatabase
                fail("Expected interrupted migration")
            } catch (_: Exception) {
                assertTrue(context.getDatabasePath(name).exists())
            } finally {
                candidate.close()
            }
            assertEquals(-9007199254740993L, success(open().balance("cash")))
        }

    @Test fun legacyDraftCodecMigratesExpenseSignsAndRelatedReferences() {
        val payload =
            """
            {
              "version": 1,
              "kind": "EXPENSE",
              "date": "2026-09-22",
              "time": "12:00",
              "accountId": "cash",
              "amount": "1234",
              "destination": null,
              "received": null,
              "merchant": "Legacy",
              "note": "",
              "purchase": "purchase",
              "amountText": "12.34",
              "allocations": [{"id": "a", "amount": "1234", "category": null}],
              "receiptLines": [],
              "tagIds": []
            }
            """.trimIndent()
        val draft = DraftRow("draft", "entry", "token", null, false, payload).model()
        assertEquals(EntryKind.EXPENSE, draft.input.kind)
        assertEquals(-1234L, draft.input.amountMinor)
        assertEquals("-12.34", draft.amountText)
        assertEquals(listOf("purchase"), draft.input.relatedEntryIds)
    }

    @Test fun signedEntryMigrationPreservesPostingsReportsAndOldPurchaseLinks() =
        runBlocking {
            migration.createDatabase(name, 5).use { old ->
                old.execSQL(
                    "INSERT INTO accounts (id,name,currency,scale,archived,includeInNetWorth,currencyLocked,groupId,type,position) VALUES ('cash','Cash','USD',2,0,1,1,NULL,'',0)",
                )
                old.execSQL(
                    "INSERT INTO accounts (id,name,currency,scale,archived,includeInNetWorth,currencyLocked,groupId,type,position) VALUES ('card','Card','USD',2,0,1,1,NULL,'',1)",
                )
                old.execSQL(
                    "INSERT INTO entries (id,token,sourceDraftId,status,kind,date,time,accountId,amountMinor,destinationAccountId,receivedMinor,merchant,note,merchantMinor,merchantCurrency,merchantScale,purchaseId) VALUES ('purchase','p','pd','POSTED','EXPENSE','2026-09-01','12:00','cash',1000,NULL,NULL,'Shop','',NULL,NULL,NULL,NULL)",
                )
                old.execSQL(
                    "INSERT INTO entries (id,token,sourceDraftId,status,kind,date,time,accountId,amountMinor,destinationAccountId,receivedMinor,merchant,note,merchantMinor,merchantCurrency,merchantScale,purchaseId) VALUES ('credit','c','cd','POSTED','REFUND','2026-09-22','12:00','card',300,NULL,NULL,'Shop','',NULL,NULL,NULL,'purchase')",
                )
                old.execSQL("INSERT INTO postings VALUES ('purchase','cash',-1000)")
                old.execSQL("INSERT INTO postings VALUES ('credit','card',300)")
                old.execSQL("INSERT INTO allocations (entryId,id,position,amountMinor,categoryId) VALUES ('purchase','p-line',0,1000,NULL)")
                old.execSQL("INSERT INTO allocations (entryId,id,position,amountMinor,categoryId) VALUES ('credit','c-line',0,300,NULL)")
            }
            migration.runMigrationsAndValidate(name, 6, true, ledgerMigration5To6).close()
            val ledger = open()
            val purchase = success(ledger.entry("purchase"))
            val credit = success(ledger.entry("credit"))
            assertEquals(-1000L, purchase.input.amountMinor)
            assertEquals(EntryKind.EXPENSE, credit.input.kind)
            assertEquals(300L, credit.input.amountMinor)
            assertEquals(listOf("purchase"), credit.input.relatedEntryIds)
            assertEquals(-1000L, success(ledger.balance("cash")))
            assertEquals(300L, success(ledger.balance("card")))
            assertEquals(1000L, success(ledger.totals(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-01"))).single().spendingMinor)
            assertEquals(-300L, success(ledger.totals(today, today)).single().spendingMinor)
            val edit = success(ledger.beginEdit(credit.id))
            success(ledger.confirm(edit.id, edit.token))
            assertEquals(300L, success(ledger.balance("card")))
            val trashed = success(ledger.trash(purchase.id, purchase.token))
            assertEquals(listOf("purchase"), success(ledger.entry(credit.id)).input.relatedEntryIds)
            success(ledger.purge(purchase.id, trashed.token))
            assertEquals(emptyList<String>(), success(ledger.entry(credit.id)).input.relatedEntryIds)
        }

    @Test fun lockedTemporaryAndCorruptStoragePreserveFiles() =
        runBlocking {
            FinancialStorage(context, name, { false }).use { assertEquals(LedgerResult.TemporarilyUnavailable, it.open()) }
            assertFalse(context.getDatabasePath(name).exists())
            val ledger = open()
            seed(ledger)
            post(ledger, expense())
            db!!.close()
            db = null
            val file = context.getDatabasePath(name)
            val before = file.readBytes()
            FinancialStorage(context, name, { true }) { _, _ -> throw IOException("Synthetic unavailable storage") }.use {
                assertEquals(LedgerResult.TemporarilyUnavailable, it.open())
            }
            assertArrayEquals(before, file.readBytes())
            val damaged = before.also { it[0] = (it[0].toInt() xor 1).toByte() }
            file.writeBytes(damaged)
            repeat(2) {
                FinancialStorage(context, name, { true }).use { assertEquals(LedgerResult.RecoveryRequired, it.open()) }
                assertArrayEquals(damaged, file.readBytes())
            }
        }

    @Test fun currencyDefinitionsAndCategoryReferencesCannotBeInvented() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            assertEquals(
                LedgerResult.Invalid(InvalidReason.CURRENCY),
                ledger.createAccount(Account("wrong-scale", "Synthetic", BookCurrency("USD", 3))),
            )
            val saved = draft(ledger, expense().copy(allocations = listOf(Allocation("a", 1234, "missing"))))
            assertEquals(LedgerResult.Invalid(InvalidReason.CATEGORY), ledger.confirm(saved.id, saved.token))
            assertEquals(saved, success(ledger.draft(saved.id)))
            assertEquals(0L, success(ledger.balance("cash")))
        }

    @Test fun concurrentConfirmationPostsOnlyOnce() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val saved = draft(ledger)
            val results = List(8) { async { ledger.confirm(saved.id, saved.token) } }.awaitAll()
            assertEquals(1, results.count { it is LedgerResult.Success })
            assertEquals(7, results.count { it == LedgerResult.AlreadyConfirmed })
            assertEquals(-1234L, success(ledger.balance("cash")))
            assertEquals(1, success(ledger.page()).entries.size)
        }
}
