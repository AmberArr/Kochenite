# Financial kernel development

The financial kernel lives in `domain/.../ledger` and `persistence/.../ledger`.
It is not yet wired into the foundation sample UI. Synthetic data only; Phase 2
setup and entry screens, receipt splits, rates and backup workflows remain later
milestones. [Financial rules](financial-rules.md) define the posting equations.

`FinancialStorage` opens `bookkeeping.db` independently of `foundation-proof.db`.
It respects credential-protected storage availability, uses the preserving SQLite
factory, and never deletes/recreates files to recover from corruption or migration
failure. All operations return typed results; cancellation propagates. Runtime
full/locked/unavailable/I/O failures are temporary, corruption/schema failures need
recovery, and arithmetic overflow is an explicit validation failure. No financial
contents are logged.

`RoomLedger` persists drafts with stable draft/transaction IDs and current-state
concurrency tokens. Saving requires the last acknowledged draft token. Explicit
confirmation validates that saved token and the original entry token, writes the
entire aggregate, checks affected balances for overflow and consumes the draft in
one transaction. Edit drafts do not change posted originals. Failure rolls back
all writes. Tokens are not revision history. A consumed confirmation cannot post
again; a repeat after a later edit/purge may return Missing instead of
AlreadyConfirmed, with no ledger effect.

Allocations and postings are normalized with foreign keys and indexed account/
category lookups. Keyset ledger pages have a maximum of 200 records and a stable
(date, local time, ID) order. Reports aggregate allocations by account currency in
SQLite. Checked subtraction derives spending after refunds and net flow. Calendar
years 1–9999 keep indexed ISO dates ordered. Draft payload v1 uses explicit JSON
with monetary values as decimal strings to preserve integer precision.

The checked-in financial Room schemas include v1, v2 and v3. The v1→v2
migration supplies a default empty transaction note; v2→v3 adds account groups,
ordering/metadata and singleton display settings. Both retain existing postings,
allocations, drafts and locked account currencies. These versions belong
to the financial database; the foundation schemas and migrations remain separate.

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
in routes. Navigation/filter state uses Android saved state; transaction drafts use
Room. The existing foundation proof activity remains available in debug builds
for its original tests and is absent from release. Search, full reports, current
rate estimates and backup workflows remain later milestones. The Add button opens
an amount-first expense/income draft; accounts are created from the Accounts tab.

The entry editor autosaves raw amount/date/time text alongside validated financial
input. Acknowledged draft writes are visible as Saved, and navigation waits for
pending persistence. Invalid posting leaves the draft available to correct.
Transaction details can start a durable edit draft; posted originals remain effective
until confirmation succeeds. Trash, restore and confirmed permanent deletion use the
same atomic posting service. Transactions lists posted, draft, incomplete and trash
states.

The editor now accepts same- and cross-currency transfers as actual sent and
received account amounts. Optional merchant amount/currency remain context and
never supply postings. Unknown actual amounts can be explicitly saved as incomplete
without changing balances or spending; completing both transfer sides removes the
incomplete marker before posting. Overview, Accounts and Reports display exact
incomplete counts and affected accounts. Refunds can be standalone or linked from
a selected purchase allocation; links provide defaults without changing the
purchase or fixing the receiving account. Partial refunds reduce spending on their
own date. Raw transfer and merchant text is checked again inside the atomic posting
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
package and instrumentation target, bounds ADB execution, and requires fifteen
kernel, five setup/storage, five UI and two process-restart assertions to finish.
Storage tests create/delete random synthetic databases. UI tests retain synthetic
records in the separate debug application. The process test force-stops only that
debug identity and verifies an acknowledged draft on a fresh process. The runner
does not modify the release app or other apps.
Sanitized results are kept in ignored `.local/financial-evidence`; failed runs
have a separate summary so they cannot replace the last successful evidence.

Tests cover exact zero/two/three-decimal amounts above the floating-point exact
range, signs, overflow, category reconciliation, transfers, missing/future amounts,
draft reopen, concurrent confirmation, stale drafts/edits, transactional failure/
cancellation, edit/trash/restore/purge/refund detachment, persistent currency lock,
bounded pages, migration preservation/rollback and non-destructive corrupt opens.
These tests also cover signed setup, reference-safe category/account edits,
navigation/form recreation, amount-first entry, entry edit/trash/restore, raw draft
roundtrips, incomplete cross-currency completion, merchant context, linked and
standalone refunds, and a forced debug-process restart. Financial release upgrades,
receipt lines, rates and backup/restore remain later gates.
