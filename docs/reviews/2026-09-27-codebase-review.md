# Codebase review — September 27, 2026

Reviewed the current working tree, including the uncommitted transaction editor
redesign, rather than only HEAD. Scope: active app, finance domain, Room storage,
schema migrations, tests, build configuration and host scripts. Local agent
configuration and the supplied, unintegrated recognition demo are outside this
review. Deferred reports/rates/backup features are not counted as implementation
defects. No production code was changed during the original review. Locations
and reproductions below describe that pre-fix snapshot.

P1 means a high-priority correctness problem; P2 means a medium-priority defect or
verification gap. Runtime evidence is identified separately from static analysis.

## Implementation follow-up

The authorized fixes split UI by feature and separate editor, list and account
state ownership; repository interfaces replace concrete Room dependencies in
application state. See [project structure](../project-structure.md).

| Finding | Fix and regression evidence |
| --- | --- |
| 1: foreign allocations | Preserve allocations unless financial inputs change; domain metadata/sign tests and a storage edit/reopen test retain corrected 700/300 shares. |
| 2: hidden bulk selections | Query changes clear selection; preview and submission derive from the same visible rows. Host query-change test and device stale-batch rollback test cover state and atomicity. |
| 3: stale transfer fields | Clear raw and parsed received amounts; domain and device tests post both Expense and Income after same/foreign-currency transfers. |
| 4: duplicate navigation completion | Editor route alone owns save completion. Other forms use originating route IDs. Host tests delay refresh and open a new editor; UI tests assert the destination after Save. |
| 5: pagination races | Reserve requests synchronously, cancel old jobs, reject stale generations/cursors and merge IDs. Host tests exercise duplicate taps, same-filter refresh and late responses. |
| 6: category loss | Preserve category IDs on same-kind selection, retaining the agreed sign reset. Domain regression covers both reselecting and changing kind. |
| 7: missing regressions | Fourteen restored financial regressions cover cancellation, overflow, corruption, populated migrations 1–6, retry, references, Unicode/filter search, bulk atomicity and foreign amounts. Separate-process and fresh-ViewModel navigation tests distinguish PID replacement from activity recreation. |

Aggregate reads now batch relations inside one transaction (five SELECTs for a
populated page instead of 201 for fifty entries). Refresh no longer fetches three
versions of the ledger page. Text input is debounced and obsolete queries are
cancelled. Unexpected storage faults return `UnexpectedFailure` with only the
exception class logged; they no longer claim financial storage is damaged.

The text query still uses substring matching, not a text-search index. The planned
50,000-record benchmark and suitable Unicode/substring indexing remain Phase 2.9
work. This follow-up does not claim a measured search latency improvement, full
visual acceptance, release validation or low-memory OS task-restoration coverage.
Current verification is recorded in the
[review-fix worklog](../worklogs/02-manual-bookkeeping.md#review-fixes-and-feature-structure--september-27-2026).

## Findings

### 1. P1 — Opening an existing foreign receipt rewrites its allocations

Locations: `domain/src/main/kotlin/ledger/TransactionEditor.kt:20`, `:141`,
`:151`; `app/src/main/kotlin/BookViewModel.kt:373` (`activate`).

`prepared()` unconditionally calls `recalculate()`. For foreign receipts,
`recalculate()` replaces every account-currency allocation with a proportional
share, even when the user has changed no amounts or items. These replaced values
also become the editor's initial state. An ordinary note edit followed by Save
therefore changes historical category spending silently.

Executable JVM reproduction against the current compiled domain:

- A valid posted input has an actual USD 10.00 charge, EUR 10.00 receipt total,
  two EUR 5.00 items, and deliberately corrected USD 7.00 / USD 3.00 category
  allocations. The domain accepts this input.
- Preparing its editor changes the allocations from `[700, 300]` to `[500, 500]`
  without a user edit. Both the original and replacement still reconcile, so
  posting validation cannot detect the unintended change.

The new UI intentionally removes editable account allocations, but existing
persisted allocations still need preservation. Initialize editors losslessly;
apply proportional recalculation only after a relevant amount/item/order change,
or require an explicit conversion of a legacy allocation. Add a note-only edit
round-trip test using a non-proportional persisted allocation.

### 2. P1 — Bulk categorization submits hidden selections absent from its preview

Locations: `app/src/main/kotlin/BookScreen.kt:1123`, `:1248`, `:1290`.

The selected-row count, currency totals and category-kind eligibility are computed
from `entries`, the current search results. The submitted batch instead comes
from the entire remembered `selected` list. Changing the query or filters does
not clear that list.

Static reproduction: select expense allocations A and B, then filter so only B
is visible. The preview reports one selected row and B's amount, but Apply sends
both A and B and changes both categories. Token validation does not reject this
when neither entry has changed. This violates the reviewable bulk-update scope.

Use one authoritative selection snapshot for preview and submission. Either clear
selection when query/filter scope changes or keep every selected row visibly
represented in the preview, including rows outside the current page/filter.

### 3. P2 — Transfer-to-expense/income transitions retain an invisible blocking field

Locations: `domain/src/main/kotlin/ledger/TransactionEditor.kt:100`, `:138`,
`:157`; `persistence/src/main/kotlin/ledger/RoomLedger.kt:514`.

`withKind()` clears `destinationAccountId` and `receivedMinor` when leaving
Transfer, but retains `receivedText`. `recalculate()` also preserves that text
for non-transfers. The persistence validator attempts to validate any nonblank
received text against the destination account, which is now null, and returns
`Invalid(ACCOUNT)`. The received field is hidden, so the new Expense/Income form
cannot explain or directly fix the failure.

Executable domain probe: USD 10.00 → EUR 9.00 Transfer, then Expense, produces
`destination=null`, `receivedMinor=null`, `receivedText="9.00"`. The rejection
path is established by the storage validator; it was not run on Android in this
review.

Clear both parsed and raw transfer-only fields when leaving Transfer. Test all
kind transitions through the posting boundary, including same-currency transfers
where the received text is populated automatically.

### 4. P2 — Successful editor saves have two independent back-stack pops

Locations: `app/src/main/kotlin/BookViewModel.kt:586`;
`app/src/main/kotlin/BookScreen.kt:559`, `:752`.

After posting, `confirmEditor()` sets the editor to null, suspends in `reload()`,
then emits `completed`. The editor route responds to null by popping the back
stack; the global completion collector also pops it. If recomposition occurs
during the reload, saving from Overview → Transactions → Editor first returns to
Transactions and then pops to Overview. Editing from a detail screen similarly
can skip the detail screen. The later unscoped event could also pop a destination
the user opened during the reload.

This is a static asynchronous-control-flow finding, not a reproduced device
journey. Give one route-scoped mechanism ownership of save completion. Distinguish
an abandoned route after process death from a successfully submitted editor, and
test the destination after saving with a deliberately delayed reload.

### 5. P2 — Repeated Load more requests can append the same page twice

Locations: `app/src/main/kotlin/BookViewModel.kt:289`;
`app/src/main/kotlin/BookScreen.kt:1354`, `:1486`.

`loadMoreSearch()` captures the current cursor, starts a coroutine, and appends
its result. It neither reserves the cursor nor marks the request busy. Two taps
before the first response therefore request the same page and append identical
entry IDs. The list uses those IDs as unique LazyColumn keys, creating duplicate
rows and a potential duplicate-key exception when they are composed.

The filter equality check does not protect against a reload or a newer request
for the same filter. Reserve requests synchronously, check a query generation
and expected cursor before applying results, and defensively merge by entry ID.
Add a delayed-repository test for repeated taps and refresh during pagination.
This finding is based on static control flow; no phone crash was induced.

### 6. P2 — Tapping the selected kind tab erases valid category selections

Locations: `domain/src/main/kotlin/ledger/TransactionEditor.kt:106`, `:116`;
`app/src/main/kotlin/TransactionEditorScreen.kt:164`.

All tabs invoke `withKind()`, including the selected tab. That function clears
every allocation's category unconditionally. Selecting Expense while already
editing an Expense silently changes categorized items to Uncategorized; the
subsequent save is valid and changes category reports.

A JVM probe confirmed `["a", "b"]` becomes `[null, null]` when reselecting Expense.
The accepted UI proposal explicitly resets the sign when selecting a kind, so
that sign behavior is not itself a finding. Preserve categories when the kind
has not changed; only invalidate them when changing category kind.

### 7. P2 — The rewritten tests remove important coverage for retained functionality

Locations: `persistence/src/androidTest/kotlin/FinancialStorageDeviceTest.kt:84`;
`persistence/src/androidTest/kotlin/BookSetupDeviceTest.kt:154`;
`app/src/androidTest/kotlin/BookUiDeviceTest.kt:216`;
`app/src/androidTest/kotlin/BookProcessDeviceTest.kt:23`.

The financial-storage suite shrank from 23 tests at HEAD to seven. Some old
draft-lifecycle tests are obsolete, but the removed tests also covered retained
behavior: cancellation rollback, overflow rollback/currency locking, corruption
preservation, interrupted migration retry, signed migration data, reference
purging, tied pagination, Unicode/filter search, stale bulk-update atomicity and
foreign receipt persistence. The current setup migration tests do not replace
those populated migration/failure fixtures.

Also, `freshEditorInputIsHeldOnlyByViewModelAndNotSavedState` recreates an Activity
and inspects the retained ViewModel. It proves rotation retention, not that
unsaved input disappears across process death or is absent from restored saved
state. The actual process-restart suite only checks explicitly saved incomplete
records.

Adapt the relevant old fixtures to direct posting instead of dropping them. Add
an actual unsaved-editor process-death/restored-navigation journey and host
ViewModel tests for navigation, async ordering and selection behavior.

## Engineering weaknesses

These are concrete architectural costs, not claims of measured device failures.

- **Read amplification and excessive refresh scope.** `RoomLedger.model()`
  (`RoomLedger.kt:616`) performs four child queries per entry. A 50-entry page
  requires 201 queries. `BookViewModel.reload()` (`BookViewModel.kt:140`) loads
  posted, trash and searched pages sequentially on every mutation; with full
  pages that is 603 queries just for entries, before accounts, totals, templates
  and incomplete records. Use batched relation queries or list projections and
  refresh only affected state. Keep aggregate reads snapshot-consistent.
- **Search output is bounded, search work is not.** `LedgerDatabase.kt:407`
  combines optional predicates with `instr(lower(...))` and correlated receipt
  lookups. There is no text-search index. A rare/no-match search can inspect all
  posted rows despite `LIMIT`. `BookViewModel.search()` starts an independent
  job on each input change without debounce/cancellation. Measure the planned
  50,000-entry workload and design indexing for the intended substring/Unicode
  semantics; do not treat a page limit as a performance guarantee.
- **Storage recovery results hide programming failures.** `storageResult()`
  (`RoomLedger.kt:70`) maps every unexpected `Exception` to `RecoveryRequired`,
  dropping the cause. A mapping bug or illegal argument is therefore presented
  like damaged financial storage. Preserve a typed unexpected-error result and
  sanitized local diagnostic context without recording financial payloads.
- **UI orchestration is difficult to isolate and test.** `BookViewModel` depends
  on concrete `FinancialStorage`/`RoomLedger` rather than read/posting interfaces,
  owns setup, accounts, categories, searches, detail state, templates and the
  editor, and uses one unscoped completion channel for all forms. `BookScreen.kt`
  is approximately 2,000 lines and performs bulk selection/preview logic itself.
  Extract cohesive screen state and domain operations, inject interfaces, and
  keep navigation completion specific to its originating operation. This would
  enable inexpensive deterministic regression tests for findings 2, 4 and 5.

## Original review verification and limits

- `./gradlew verify`: passed. Domain: 16 tests (9 ledger, 7 editor). Host scripts:
  10 tests. Lint: no errors; app 49 warnings and one hint, persistence 6 warnings.
  Dependency-update warnings are not treated as bugs merely because a newer
  dependency exists.
- The initial Gradle command failed because the existing wrapper-cache lock at
  `/home/amber/.local/share/gradle/wrapper/dists/gradle-9.5.0-bin/bvnork1r7n8i6kp5cnkibsc9q/gradle-9.5.0-bin.zip.lck`
  was read-only in the sandbox. The supported access escalation allowed the run;
  no caches or environment variables were redirected.
- An isolated `/tmp` Kotlin probe compiled against the current domain classes
  reproduced findings 1, 3 and 6. It did not change the repository's source or
  installed app. Finding 3's final storage rejection is a static trace.
- `./gradlew :app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest`:
  passed. This compiles/packages the tests; it does not execute them on a device.
- Final whitespace verification is recorded in the linked session worklog below.
- No phone tests, release installation, process termination or performance
  benchmark were performed. UI race effects still require device confirmation.
- The existing atomic posting boundary, optimistic tokens, exact arithmetic,
  corruption-preserving SQLite factory and OS-backup exclusions are useful
  foundations. They do not eliminate the editor/state regressions above.

Session evidence: [Phase 2 worklog](../worklogs/02-manual-bookkeeping.md#codebase-review--september-27-2026).
