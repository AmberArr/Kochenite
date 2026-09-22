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

The checked-in financial Room schemas include v1 and v2; the first additive
migration supplies a default empty transaction note while retaining the existing
postings, allocations, drafts and locked account currencies. These versions belong
to the financial database; the foundation schemas and migrations remain separate.

## Verification

From the workspace root, with the existing default caches:

```sh
./gradlew verify :persistence:assembleDebugAndroidTest
adb devices -l
adb -s "$ANDROID_SERIAL" shell am get-current-user
adb -s "$ANDROID_SERIAL" install --user 0 -r persistence/build/outputs/apk/androidTest/debug/persistence-debug-androidTest.apk
node scripts/run-financial-tests.mjs "$ANDROID_SERIAL"
```

Set `ANDROID_SERIAL` to the explicitly selected currently connected reference
phone and substitute the discovered Android user if it is not 0. Never create an
emulator or clear app data. The runner verifies the isolated debug storage test
package and instrumentation target, bounds ADB execution, and requires all twelve
financial tests to finish successfully. Only random synthetic test databases are
created/deleted. It does not launch or modify the release app or other apps.
Sanitized results are kept in ignored `.local/financial-evidence`; failed runs
have a separate summary so they cannot replace the last successful evidence.

Tests cover exact zero/two/three-decimal amounts above the floating-point exact
range, signs, overflow, category reconciliation, transfers, missing/future amounts,
draft reopen, concurrent confirmation, stale drafts/edits, transactional failure/
cancellation, edit/trash/restore/purge/refund detachment, persistent currency lock,
bounded pages, migration preservation/rollback and non-destructive corrupt opens.
These tests establish storage behavior, not UI/process-death entry journeys or
release upgrades of the financial database. Those are explicit later gates.
