# Financial kernel development

The financial kernel lives in `domain/.../ledger` and `persistence/.../ledger`;
pure editor transformations live in `domain/.../editor`. See the
[project structure](project-structure.md) for feature and repository ownership.
The Phase 2 app uses it for manual entry, accounts, categories and receipt splits.
Synthetic data only; reports/rates and backup workflows remain later milestones.
[Financial rules](financial-rules.md) define posting equations and state changes.

`FinancialStorage` opens `bookkeeping.db`. It respects credential-protected
storage availability, uses the preserving SQLite factory, and never
deletes/recreates files to recover from corruption or migration failure. All
operations return typed results; cancellation propagates. Runtime
full/locked/unavailable/I/O failures are temporary, corruption/schema failures need
recovery, and arithmetic overflow is an explicit validation failure. Unexpected implementation faults return a separate unexpected-failure result
and log only their exception class. No financial contents are logged.

Ordinary manual editor input exists only in the ViewModel. New/edit/copy/template
initialization is read-only; a direct Save validates the complete input, checks its
stable submission identity and expected posted-record token, replaces the entire
aggregate and checks affected balances in one database transaction. A failure or
stale token leaves the original effective. Repeated submission identities cannot
post twice. Only an explicitly saved incomplete record is persisted in the legacy
`drafts` table; its token-checked completion consumes it in the same transaction
that posts the entry. Tokens describe current state, not revision history.

Allocations and postings are normalized with foreign keys and indexed account/
category lookups. Keyset ledger pages have a maximum of 200 records and a stable
(date, local time, ID) order. Reports aggregate signed Expense/Income allocations by account currency in
SQLite. Expense credit and income reversal signs determine report contributions;
account postings use the signed actual amount directly. Calendar years 1–9999 keep
indexed ISO dates ordered. The legacy editor payload codec remains backward-readable
for explicit incomplete records and saved templates; v2 stores generic related-
transaction references and upgrades v1 positive expenses and purchase links.
Monetary payload values are decimal strings to preserve integer precision.

The checked-in financial Room schemas now include v1–v7. The v5→v6 migration
adds generic ordered transaction references, migrates legacy purchase links,
converts old Expense amounts to signed values and maps Refund records to positive
Expense credits without changing postings or historical report contributions.
The v6→v7 data migration deletes legacy ordinary editor rows and keeps explicitly
saved incomplete rows. Draft/template JSON is upgraded by a version-aware decoder.
Earlier migrations retain existing postings, allocations, templates, incomplete
records and locked account currencies. These versions belong to the financial database.

Phase 2.2 adds focused account, category and settings repositories. Setup creates
one named account, its signed opening posting, optional starter categories and
display-currency settings in one transaction. Account groups are organizational;
archival and net-worth inclusion remain independent account settings. Editing
uses expected current records; a posted account's currency cannot change, even
after purge. Categories allow a direct parent allocation or one child level;
archiving a parent requires archiving active children first. Separate virtual
Uncategorized income/expense options correspond to null category IDs.

The main app now uses [typed Navigation Compose routes](https://developer.android.com/guide/navigation/design/type-safety)
for Overview, Transactions, Accounts, Reports and settings/forms. Only IDs travel
in routes. Navigation/filter state uses Android saved state; active editor input is
held in the ViewModel only. A restored editor route closes after process death
rather than restoring abandoned input. The Transactions list uses date groups,
status filters, amount-first signed Expense/Income entry, allocation/receipt details and general related-transaction
references. The Add button opens a focused in-memory editor; accounts are created
from the Accounts tab. Full reports, current-rate estimates and backup workflows
remain later milestones.

Transaction and item input remains in memory until one Save transaction succeeds.
Rotation and temporary backgrounding retain it while the ViewModel survives;
process termination loses it. Back closes an unchanged form and asks before
discarding changed input. Save errors retain the editor. Transaction details,
copying and templates initialize unsaved editors without storage writes. Trash,
restore and confirmed permanent deletion use token-checked atomic operations.
Transactions lists posted, incomplete and trash states; there is no manual Drafts
tab or ordinary editor save-status message.

The editor accepts same- and cross-currency transfers as actual sent and received
account amounts. A foreign-currency transaction amount is separate from its actual
account amount; switching currencies never converts typed numbers or infers a
charge. Unknown actual amounts can be explicitly saved as incomplete without
changing balances or spending; completing both transfer sides consumes the
incomplete record while posting. Overview, Accounts and Reports display exact
incomplete counts and affected accounts. Refunds can be standalone or linked from
a selected purchase allocation; links provide defaults without changing the
purchase or fixing the receiving account. Positive Expense credits reduce spending on their own posting date. Related
transactions are selected as contextual links; copies and templates clear them. Raw transfer and merchant text is checked again inside the atomic posting
service, so malformed or mismatched editor values cannot be ignored at confirmation.

## Verification

From the workspace root, with the existing default caches:

```sh
./gradlew verify :app:assembleDebug :app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest
adb devices -l
adb -s "$ANDROID_SERIAL" shell am get-current-user
adb -s "$ANDROID_SERIAL" install --user 0 -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install --user 0 -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" install --user 0 -r persistence/build/outputs/apk/androidTest/debug/persistence-debug-androidTest.apk
node scripts/run-financial-tests.mjs "$ANDROID_SERIAL" --ui
```

Set `ANDROID_SERIAL` to the explicitly selected currently connected reference
phone and substitute the discovered Android user if it is not 0. Never create an
emulator or clear app data. The runner verifies the isolated debug storage test
package and instrumentation target, bounds ADB execution, and requires seven
financial-storage, fourteen ledger-regression, six setup, seven UI and four
process-restart tests to finish.
Storage tests create/delete random synthetic databases. UI tests retain synthetic
records in the separate debug application. The process test force-stops only that
debug identity and checks both explicit incomplete survival and unsaved editor
loss in a different PID. A separate fresh-ViewModel recreation test checks the
restored editor route returns to its parent. Force-stop does not itself prove
Android low-memory task restoration. The runner
does not modify the release app or other apps.
Sanitized results are kept in ignored `.local/financial-evidence`; failed runs
have a separate summary so they cannot replace the last successful evidence.

Tests cover exact zero/two/three-decimal amounts above the floating-point exact
range, signs, overflow, category reconciliation, transfers, missing/future amounts,
concurrent submission, stale edits/incomplete tokens, transactional failure,
edit/trash/restore/purge/reference detachment, persistent currency lock, bounded
pages, migration preservation/rollback and non-destructive corrupt opens. The
v6→v7 migration test proves ordinary editor rows are retired while incomplete rows
remain. Tests also cover signed setup, reference-safe category/account edits,
navigation/form recreation, read-only editor initialization, direct posting, explicit
incomplete save/update/delete/completion, cross-currency values, merchant context,
signed Expense credits, transaction references and a forced debug-process restart.
Financial release upgrades, the visual/device matrix, large-ledger performance,
rates and backup/restore remain later gates.
