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

## Milestone 2.6 — September 23, 2026

Added Room schema v5 for tags, entry-tag links and reusable templates with a
preserving v4→v5 migration. Draft JSON remains backward compatible. Posted and
trashed ledger entries now have bounded keyset search over merchant/payee,
notes and receipt-line descriptions, with date, account, category including
direct/child allocations, tag, kind and selected-currency amount filters.
Draft and incomplete status views remain separate lists; their posted/trash
search controls are hidden so filters never appear to apply when they do not.

Tags can be created, renamed and archived. Copying makes a new today-dated
draft with new entry and allocation IDs and no purchase/refund link. Templates
retain selected fields and optional amounts/splits; applying one creates a
draft and never posts automatically. Archived or missing referenced selections
must be corrected before confirmation. Allocation-row bulk categorization
previews selected row count and totals by currency, compares all source tokens
inside one write transaction and updates only category assignments. Stale
selections and injected failures roll back the entire batch.

Verification: `./gradlew spotlessApply verify :app:assembleDebug
:app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest` passed.
On the rediscovered OnePlus 6T, the explicit-serial runner passed 36 assertions:
21 financial storage, five setup, eight UI and two process-restart checks.
New tests cover v4→v5 preservation, Unicode and combined filters, tag archival,
copy/template draft identity, stale/rollback bulk behavior, and UI journeys for
search, copying, templates and a selected allocation category change with exact
balance/spending preservation. Sanitized results are in ignored
`.local/financial-evidence/instrumentation.json`.

During UI verification, returning from a detail route could restore a saved
detail rather than the Transactions tab. Tab navigation now pops to an
already-open tab, retaining its filter state. A long transaction list also
required scrolling to the filter bar before a test could tap Trash; the
physical-device journey passed after this correction. Only synthetic debug
records were used. Milestones 2.7–2.9 remain pending; real bookkeeping is
still gated by final validation and the separate signing-key backup check.

## UI review — September 23, 2026

Reviewed Phase 2 requirements, Compose navigation/presentation and the current
debug build on the rediscovered OnePlus 6T. Screenshots of Overview,
Transactions, Accounts, Reports and Settings are saved in ignored
`.local/ui-review-2026-09-23/`; visible records are synthetic. The walkthrough
did not edit financial data or app code.

Review findings for information architecture and presentation: the app-name
top bar duplicates each destination heading, and Back remains visible on a
primary destination after tab navigation; give root destinations their own
titles and reserve Back for pushed child flows. The Transactions page starts
with four unselected status actions followed by the full search/filter form,
so the ledger is below the initial viewport; show the selected status clearly,
keep search/ledger visible first, and put less-used filters behind a compact
disclosure or sheet. The entry editor is one long form: merchant currency,
receipt/split controls, tags and notes compete with amount, account, date and
posting actions; order the primary fields first, group existing optional
sections, and keep the explicit post action easy to reach. Template management
is reached from Transactions although the accepted IA places template
management in Settings; retain an entry point where users apply templates and
place its management entry in Settings. Overview currently gives account rows
the same plain-text treatment as balances. Reports shows a month-to-date
summary only; milestone 2.7 remains pending, and its range, currency totals and
transaction drilldown should get a clear hierarchy while preserving filter
state. These are presentation recommendations only; no financial semantics or
product scope changed.

Inspection used `adb devices -l`, explicit-serial `adb shell dumpsys` and
`screencap`, read-only source/plan inspection, and focused ui-ux-pro-max searches.
No build or tests were run because this was a UI audit without implementation.

## Transaction list/editor proposal — September 24, 2026

Produced [the concrete UI proposal](../../docs/transaction-ui-proposal.md) for
Transaction List and Create/Edit Transaction only. It recommends date-grouped
posted/trash rows with aligned actual amounts, explicit kind/status labels,
compact search and filter access, an amount-led editor, collapsed secondary
metadata, and a focused split/receipt panel sharing the existing draft. The
proposal specifies state treatment, interaction paths, Compose layout targets
and later verification checks. No implementation, domain, storage, query or
financial-rule changes were made; no milestone status changed. The proposal
is a recommendation, not an accepted expansion of Phase 2 scope.

Read current plans, worklog, Git status and relevant presentation/editor/query
code; used the September 23 Kochenite screenshots. `adb devices -l` rediscovered
the OnePlus 6T. Bluecoins launched, but `screencap` was black and window focus
reported NotificationShade, so its current layout and tap counts could not be
verified. An initial package-list command also reported denied access to user
11; inspection stayed with the foreground user-0 apps. No Bluecoins data was
edited, saved, cleared or uninstalled. Returned the foreground app request to
Kochenite debug. Ignored capture evidence: `.local/ui-review-2026-09-24/`.

Verified proposal content against existing search scope, draft ordering,
explicit posting, autosave and split/item behavior. `git diff --check` passed;
no build/device tests were run for this documentation-only pass. Large-text,
dark-theme, keyboard and proposed-layout behavior remain unimplemented and
unverified. Next UI action, if requested, is implementation of this bounded
proposal; the scheduled 2.7 milestone remains pending.

## Bluecoins reference retry — September 24, 2026

Retried the previously blocked read-only UX inspection on the rediscovered
OnePlus 6T. The foreground Bluecoins activity and screencap now worked. The
transaction list uses a period heading with a total, flat date-context rows,
strong trailing signed amounts, a leading category/kind icon with primary
merchant and category count, and quieter account metadata. Search/filter
controls are in the compact top action row. One Add tap opens the entry form;
its name, date/time and prominent amount are near the top, account/category,
split, label and note are below, and type/save actions remain at the bottom.
The initial focus is Name. Opening a transaction and tapping Edit took two
taps. Detail emphasizes merchant, date/time, total and allocation/category
rows; the edit view keeps allocation amounts and categories visible.

No value was entered, edited or saved. The empty Add form was exited, the
foreground request returned to Kochenite debug, and no Bluecoins data was
cleared or uninstalled. Read-only captures stay ignored under
`.local/ui-review-2026-09-24/bluecoins-*.png`; no transaction contents are
transcribed here. The UI proposal now records these observations and uses them
as density/hierarchy evidence while retaining Kochenite's amount-first
requirement, Material look and explicit textual transaction-kind labels.
`git diff --check` is the documentation verification; no app build or test was
needed. No product or financial decision changed.

## Unified allocation UI proposal — September 24, 2026

Following the user's direction, revised `docs/transaction-ui-proposal.md` to
remove the separate split/item interaction modes. The recommendation is one
allocation-row editor: category and account-currency amount per row, with
optional receipt-line amount and optional item title on the same row. Ordinary
one-category entry still needs only its Category selector; repeated rows stay
in the focused child editor on the same draft. Receipt lines remain one-to-one
with allocations and retain both reconciliation checks.

The current domain rejects blank receipt descriptions, so the proposed optional
title requires relaxing only that nonblank-text check. Positive line amounts,
merchant receipt total, account allocation total, postings, persistence and
report semantics stay unchanged. No implementation was made. Read-only review
of `domain/src/main/kotlin/ledger/Ledger.kt` and
`domain/src/test/kotlin/ledger/LedgerTest.kt` confirmed these current
constraints. `git diff --check` passed; no build or tests were needed for this
design update.

## Signed Expense/Income UI direction — September 24, 2026

Updated `docs/transaction-ui-proposal.md` after the user's direction to remove a
separate Refund kind: the proposed selector now has Expense, Income and
Transfer, with signed Expense/Income amounts and positive Expense representing
a refund/credit. A linked purchase remains contextual metadata. Negative Income
is described as an income reversal. No app, persistence, domain or report code
changed. The current implementation still uses a distinct Refund kind; signed
report mapping, absolute-amount allocation reconciliation and migration need a
coherent contract before implementation. `git diff --check` passed; no build or
tests were run.

## General related-transaction references — September 24, 2026

Updated `docs/transaction-ui-proposal.md` after the user's clarification that
Refund should not be a separate transaction kind and that transaction
references should be a general feature. The proposal now presents references
as secondary context in the editor, independently of kind, with no effect from
the reference itself on amount, category, posting or reports. It intentionally
does not set reference cardinality, eligible-record rules or copy behavior.
The current app still has a separate Refund kind and purchase-only link/action;
no application or domain behavior changed. Signed report/allocation rules and
the reference contract remain open for a future implementation decision.
`git diff --check` is the documentation verification; no build or tests were
run.

## Transaction-row labels and adjustment identity — September 24, 2026

Revised `docs/transaction-ui-proposal.md` after the user's feedback. Ordinary
Expense and Income rows no longer repeat a kind label; signed amount, merchant
and category provide the scan hierarchy. Transfers are identified by their
source/destination pair. Recommended keeping opening balances and later balance
corrections in a dedicated balance-only Adjustment family, with “Opening
balance” and “Balance adjustment” row titles so their roles remain clear. The
existing account-creation and account-adjustment flows remain the entry points;
no app or domain behavior changed. This preserves their distinction from
income/spending activity. `git diff --check` is the documentation verification;
no build or tests were run.

## Transaction UI implementation verification — September 23, 2026

Implemented the authorized [transaction UI proposal](../../docs/transaction-ui-proposal.md)
and the approved general reference contract. The ledger now uses grouped rows,
status tabs, compact search/filter and overflow actions, accessible selection,
and explicit loading/empty/error states. The editor is amount-led, keeps posting
available, and uses one allocation editor for category splits and optional
receipt-line details. Expense/Income direction is signed; Refund is no longer a
distinct kind. Existing expense/refund records and drafts migrate without
changing postings or historical totals. Receipt titles may be blank while both
allocation and receipt totals still reconcile exactly.

Related references now support multiple posted transactions of any kind, stay
unchanged while editing, and clear when copying or applying a template. Trash
keeps existing links; purge detaches links and invalidates affected edits.
References have no financial effect. Room schema v6 and the legacy draft codec
preserve old draft/template data and legacy purchase links. The master plan,
phase plan and financial rules record the resulting decisions.

`./gradlew spotlessApply verify :app:assembleDebug :app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest`
passed. After aligning the UI test with the new editor/list overflow menus,
`./gradlew spotlessApply verify :app:assembleDebugAndroidTest` passed.
The focused `tagSearchCopyAndTemplateKeepNewWorkAsDrafts` test passed on the
rediscovered OnePlus 6T. `node scripts/run-financial-tests.mjs
adb-a2480128-JKi84V._adb-tls-connect._tcp --ui` completed with 38 assertions:
23 financial-storage, 5 setup, 8 UI and 2 process-restart assertions. The suite
covers signed postings/reports, Room migration and recovery, related-reference
lifecycle, copy/template behavior, editor journeys and saved-draft recovery.
`git diff --check` passed after final plan/worklog updates. APKs were installed
with `adb install -r`, preserving the debug
app's existing data. Tests used generated synthetic records; no app data was
cleared.

Acceptance gap: visual review at large text and in dark/wide layouts, and a
dedicated keyboard/Back/system-bar stress pass, were not performed. The phone
run covers its compact default viewport, typed editor input, activity
recreation and process restart. Keep the remaining visual/accessibility matrix
in milestone 2.9; it does not block this verified implementation increment.
Next: begin milestone 2.7 reports and current-rate estimates.


## Transaction editor visual refinement — September 24, 2026

Refined the editor in `app/src/main/kotlin/BookScreen.kt` without changing field order or transaction behavior. Amount is now an unoutlined hero with visible currency, direct text input, and an accessible compact sign toggle. Transaction kind uses a compact selector; account, destination, category, and date/time use left-aligned label/value rows with trailing chevrons. Merchant/payee remains a text input. Allocation details and More details are low-emphasis action rows, and Save incomplete now sits beside Post in the fixed footer. Added the Account and sign-toggle accessibility strings in `app/src/main/res/values/strings.xml`.

Updated `BookUiDeviceTest` to click the footer Save incomplete action after its relocation. `./gradlew spotlessApply verify :app:assembleDebug :app:assembleDebugAndroidTest` passed. The updated APKs were installed over the existing debug installation with `adb install -r`, preserving its data. `node scripts/run-financial-tests.mjs adb-a2480128-JKi84V._adb-tls-connect._tcp --ui` completed all 38 financial, setup, UI, and process-restart assertions. The editor was visually inspected on the connected OnePlus 6T; dark-theme, wide-layout, and dedicated keyboard/system-bar stress coverage remain in milestone 2.9. `git diff --check` passed.
