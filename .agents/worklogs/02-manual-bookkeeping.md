# Phase 2 worklog

Plan: [milestones](../plans/phases/02-manual-bookkeeping.md).

## September 22, 2026 — Session start

Read master/Phase 1 plans, latest worklog, existing domain/storage/build/device
patterns and Git status. Existing Phase 1 worklog modification, `.codex/` and
`receipt-ai-demo/` belong to the user and remain unstaged. Accepted revised Phase 2
scope supersedes old master-plan encryption/history/currency/receipt/navigation
assumptions. Reference OnePlus 6T discovered online via `adb devices -l`.
Implementation and verification evidence follows as milestones complete.

## Milestone 2.0

Recorded all ten milestone deliverables/exits and updated the master decisions to
match the supplied plan. Added financial equations, 21 independent worked examples,
foreign receipt rounding, parent rollups and durable draft/edit/trash transitions.
Examples specify postings, allocations, resulting balances and period report totals.
No kernel product decision remains unresolved. Documentation links and
`git diff --check` pass; no app build needed for this documentation increment.
Protected `.agents` writes required approved access; no cache/path workaround used.

## Milestone 2.1 — September 23, 2026

Implemented independent pure Kotlin money/currency, local date/time, transaction
kinds and validation; normalized financial Room accounts/categories/entries/postings/
allocations with durable JSON drafts, indexed bounded ledger pages and database-side
report aggregates. Added FinancialStorage for `bookkeeping.db`; foundation files
and the sample UI remain untouched. Current-state tokens reject stale draft saves,
confirmations and edits without revision history. Confirm consumes the draft
atomically; cancellation and injected storage failure roll back replacement.
Trash/restore/purge use the same transactional boundary; surviving refunds detach
with their amounts preserved and stale edit tokens invalidated. Currency locking
survives purge. SQLite and domain arithmetic overflow are surfaced explicitly.

Generated financial v1 and v2 schemas and an additive note migration with supported
upgrade/failed-upgrade/reopen fixtures. Financial draft schema v1 stores exact money
as strings. Added a bounded, explicit-serial financial device runner and a kernel
build/test guide. Failed runner summaries cannot overwrite successful evidence.

Verification:

- `./gradlew spotlessApply verify :persistence:assembleDebugAndroidTest` passed:
  seven new ledger JVM tests plus six existing JVM tests, ten host script tests,
  formatting and both Android lint checks. No lint errors.
- Installed only `moe.frost.kochenite.debug.storage.test` with `adb -s` on the
  rediscovered OnePlus 6T (Android user 0), using `install --user 0 -r`.
- `node scripts/run-financial-tests.mjs <selected serial>` passed all 12 physical
  financial assertions. Covered exact reopen/reporting, draft consumption, eight
  simultaneous confirmations (one success), stale saves/edits, failed/cancelled
  replacement, overflow rollback including currency lock, trash/restore/purge with
  refund detachment, incomplete/future input, bounded pagination, migration and
  interrupted-migration retention, locked/temporary/corrupt storage preservation,
  and invalid currency/category references. Evidence: ignored
  `.local/financial-evidence/instrumentation.json`.
- Runner rejects missing serial; `node --check` and `git diff --check` passed.

Resolved build issues: default Gradle wrapper cache was sandbox read-only; approved
access used without redirection. Kotlin required an explicit nullable SQL argument
array type in the migration fixture. Long expressions were simplified to satisfy
pinned ktlint; the repository formatting configuration remains unchanged.

Coverage limits: kernel/storage only, no financial UI/recreation/process-kill
journeys or signed financial upgrade yet. No Phase 2.2+ completion claimed. No
release/Bluecoins/user financial data modified. Off-machine signing backup remains
required for the 2.9 real-use gate. Next action: milestone 2.2.

## Milestone 2.2 — September 23, 2026

Implemented offline setup, explicit account/display currencies, typed four-destination
Navigation Compose, responsive navigation rail/list-detail, and account, group and
category management. Signed opening balances and adjustments affect only balances.
Account currency remains locked after any posting, including purge. Category direct
and child totals roll up once. Foundation proof remains accessible in a debug-only
activity; its database is untouched. Financial schema v3 adds book settings, account
groups and account metadata through a preserving migration.

Verification: `./gradlew verify :app:assembleDebug :app:assembleDebugAndroidTest
:persistence:assembleDebugAndroidTest` passed (formatting, lint, JVM, host tests and
APK builds). The explicit-serial physical-device runner passed 19 financial/setup/UI
assertions, including recreation and v2→v3 migration; evidence is in ignored
`.local/financial-evidence/instrumentation.json`. Installed the debug APK only on
the rediscovered OnePlus 6T, verified the app was top-resumed, and inspected its
Overview rendering and teal control. All records used were synthetic.

Scope limit: the current Add control creates accounts until 2.3 entry is wired.
Transactions shown in the app are read-only. No real-bookkeeping handoff or Phase 2
completion is claimed. Next action: implement milestone 2.3.
