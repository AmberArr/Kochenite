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

## Milestone 2.3 — September 23, 2026

Added amount-first expense/income entry, Room-backed raw editor text, debounced
autosave with visible save status, Back flushing, draft resume/discard, explicit
confirmation, details, durable edit drafts and posted/draft/incomplete/trash views.
The detail view provides trash, restore and confirmed permanent purge. Current
financial records are replaced atomically; stale tokens and invalid posting retain
the draft and leave the posted original intact. Draft payload v1 gained optional
raw text fields without a Room schema change, so older drafts still decode.

Verification: `./gradlew spotlessApply verify :app:assembleDebug
:app:assembleDebugAndroidTest` passed, including formatting, lint, JVM/host tests
and builds. The explicit-serial physical-phone runner passed 23 assertions: 13
kernel/storage, five setup, three UI and two process-restart. UI coverage includes
recreation with an acknowledged draft, exact expense posting, editing, trash and
restore. The runner force-stopped only the debug app between the two process tests,
then read and discarded its own synthetic draft. Evidence is sanitized in ignored
`.local/financial-evidence/instrumentation.json`. No real financial or release app
data were touched.

The first combined run exposed an editor-test race: the field label was queried
before the asynchronous edit route had an editable node. Stable field tags and a
wait for the editable node fixed this; the full 23-assertion runner passed. Existing
2.1 storage tests cover failed/cancelled posting, stale edits and atomic rollback.
The editor surfaces save failures while retaining current input. Current list pages
are bounded; search and richer filtering belong to 2.6. Incomplete creation,
transfers and refunds belong to 2.4. Next action: milestone 2.4.

## Milestone 2.4 — September 23, 2026

Extended the entry editor for actual sent/received transfer amounts, optional
merchant-currency context, explicit Save incomplete, and linked or standalone
refunds. Linked refunds start from a selected purchase allocation with editable
amount, category and receiving account. Incomplete transfers have no postings or
authoritative allocations; after both amounts are supplied the saved entry becomes
an ordinary draft and posts only on explicit confirmation. Exact incomplete counts
and affected-account markers appear in Overview, Accounts and Reports. The posting
service revalidates raw editor text in the transaction, preventing malformed or
mismatched transfer/merchant values from being silently dropped. No tax, discount
or quantity model was introduced.

Verification: `./gradlew spotlessApply verify :app:assembleDebug
:app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest` passed.
The explicit-serial physical-phone runner passed 27 assertions: 15 financial
storage, five setup, five UI and two process-restart. New UI journeys cover an
incomplete USD→JPY transfer with actual received amount, a merchant-currency
context value that never changes reports, a linked partial refund to a different
account and a standalone refund. Storage tests cover same-currency equality,
cross-currency actuals, malformed raw fields, multiple partial refunds and refund
periods. Sanitized evidence: ignored `.local/financial-evidence/instrumentation.json`.
Only synthetic debug data were used; release/user financial data and other apps
were untouched.

During verification, a long account dropdown caused a UI test to tap a visible
option while its intended target was offscreen. Tagged choices plus explicit
scrolling made the test select and verify the requested account. The complete
27-assertion runner passed afterward. Incomplete draft listing remains a simple
scrolling view; indexed search/pagination comes in 2.6. Next action: milestone 2.5.

## Milestone 2.5 — September 23, 2026

Added category-split rows and optional final-inclusive receipt lines to the
entry editor. Each line has a description and merchant-currency total tied by
stable ID to one account-currency allocation and category. The editor displays
both account and receipt reconciliation, proposes exact foreign allocations
with stable largest-remainder rounding, and lets users correct each allocation.
Line and allocation removal/editing replace the aggregate atomically; invalid
totals retain the draft and leave the posted transaction unchanged. Zero-minor
allocations are valid for itemized receipts where a small account charge cannot
give every positive merchant line one minor unit. Linked refunds can be started
from a displayed receipt item and carry its description as context. Room schema
v4 stores receipt lines under allocations and migrates v3 data without change.
No tax, discount, quantity, coupon or points shortcut was added.

Verification: `./gradlew spotlessApply verify :app:assembleDebug
:app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest` passed.
The rediscovered OnePlus 6T physical-phone runner passed 30 assertions: 17
financial storage, five setup, six UI and two process-restart checks. New tests
cover v3→v4 migration, receipt edit rollback/replacement across reopen, exact
and large-number rounding, and a UI journey that enters foreign receipt lines,
reviews their account allocations, posts once and checks the resulting balance.
The existing incomplete/refund UI tests initially raced asynchronous navigation;
waiting for the target route resolved this and the full runner passed. Sanitized
evidence is in ignored `.local/financial-evidence/instrumentation.json`. Only
synthetic debug records were used. Next action: milestone 2.6.
