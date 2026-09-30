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

## Transaction editor redesign check — September 25, 2026

Kept the existing Spotless 8.10.2 and ktlint 1.8.0 configuration. The first
`./gradlew spotlessApply verify :app:assembleDebugAndroidTest
:persistence:assembleDebugAndroidTest` run stopped on one ktlint
`if-else-wrapping` finding; after expanding the focus block, formatting passed,
`spotlessCheck` passed, all 10 host script tests passed, and domain sources and
tests compiled. `:domain:test` then failed three new editor assertions: the JPY
precision transition accepted excess fractional digits, one currency merge
assertion checked an internal signed string instead of the displayed amount,
and the last-item test deleted the categorized item before checking category
restoration. Updated the parser and test cases; those fixes have not been rerun.
The build did not reach successful app/persistence instrumentation packaging;
no device suite or visual review was run. `git diff --check` passed. Next: rerun
the Gradle command, resolve any remaining failures, then continue phone review.

## Setup-button device check — September 25, 2026

The setup helper was submitting while the last currency field still owned the
keyboard, leaving `Create book` below the visible scroll viewport on the phone.
It now scrolls the tagged button into view, asserts it is displayed, and then
uses Compose `performClick`; the test does not depend on an Enter key event.
`./gradlew spotlessApply :app:assembleDebugAndroidTest` passed, and the rebuilt
instrumentation APK installed successfully on the OnePlus 6T. After another
debug-data reset, the focused `unchangedEditorClosesWithoutDiscardConfirmation`
device test passed from the blank setup screen, including account creation and
transition into the main app. A manual touch path with synthetic values
(`Synthetic cash`, `USD`, `100.00`, `USD`) also showed the new account at
`USD 100.00`. `git diff --check` passed.

## Split-list entry behavior — September 25, 2026

Changed the parent `+ Split transaction` action so it opens the Transaction
items page without creating an implicit item or opening an item sheet. `+ Add
item` remains the explicit creation action and still opens the new item sheet.
Updated the focused UI journey and transaction UI proposal/phase plan. Ran
`./gradlew spotlessApply :app:assembleDebug :app:assembleDebugAndroidTest`
before installing both APKs over the existing debug app. The focused physical-
device test `itemChangesRemainInMemoryUntilParentTransactionIsSaved` passed on
the connected OnePlus 6T (`OK (1 test)`), including the empty-list first tap,
explicit `+ Add item`, in-memory edits, and parent Save. `git diff --check`
passed after the update.

## Date/time picker layout fix — September 25, 2026

The date picker was rendered inside the default 280dp `AlertDialog` width,
which clipped the rightmost weekday and dates on the 384dp reference phone.
Replaced that container with a full-width custom dialog constrained by phone
gutters, added a selected Date/Time tab indicator, and matched the dialog and
picker surface colors. Date and time values still commit together only when
Done is pressed; Cancel and outside dismissal leave the editor unchanged.

`./gradlew spotlessApply :app:assembleDebug :app:assembleDebugAndroidTest`
passed after the implementation. The final debug APK was installed over the
existing debug app without clearing data. Physical-device captures on the
OnePlus 6T show all seven calendar columns and the complete time dial in both
tabs; `git diff --check` passed.

## Date/time picker color cleanup — September 26, 2026

After restoring the default Material 3 theme, removed the temporary
component-specific picker color overrides. The date/time dialog now relies on
Material 3's normal picker defaults; the phone-width layout fix remains because
it prevents calendar clipping. No build or device verification was requested
for this cleanup.

## Retire Phase 1 sample proof — September 26, 2026

Removed the obsolete sample-only activity, ViewModel, domain model, Room database,
storage wrapper, schema snapshots, and their unit/device tests. Removed the old
sample instrumentation and release-journey scripts; current financial
instrumentation and APK checks remain. Moved the default light/dark Material 3
wrapper into `KocheniteTheme.kt` and removed its dependency on the sample screen.
Pruned sample-only strings and the now-unused app JVM-test dependencies.

Updated the active master/Phase 2 plans, build guide, financial-kernel notes and
README to describe the financial app. Kept the Phase 1 plan and worklog as
historical verification, and recorded that any old `foundation-proof.db` on a
device is neither opened nor deleted. No build, test or phone check was run, per
the user's instruction. `git diff --check` passed.

## Codebase review — September 27, 2026

Reviewed the active working tree, including the existing uncommitted editor
redesign, at the user's request. Recorded seven prioritized correctness/testing
findings plus architectural weaknesses in the
[review report](../reviews/2026-09-27-codebase-review.md). No application code,
test source, existing staged content or device data was changed. Updated plan
status to distinguish passing host checks from unresolved review/device work.

`./gradlew verify` passed: 16 domain tests and 10 host-script tests; Android lint
reported zero errors (app: 49 warnings and one hint; persistence: six warnings).
The initial sandboxed invocation failed with `FileNotFoundException` / read-only
filesystem for the existing Gradle wrapper cache lock under
`/home/amber/.local/share/gradle/wrapper/dists/gradle-9.5.0-bin/`.
The supported access escalation allowed verification without cache redirection
or environment changes. `./gradlew :app:assembleDebugAndroidTest
:persistence:assembleDebugAndroidTest` also passed. `git diff --check` and an
explicit whitespace check of the new, untracked review report passed.

An isolated Kotlin probe in `/tmp/kochenite-review-kenz8ba7/Review.kt`, compiled
against the current domain classes, demonstrated: opening a valid foreign
receipt replaces corrected `[700, 300]` allocations with `[500, 500]`; switching
Transfer to Expense retains nonblank received text despite removing its
destination; reselecting Expense clears existing category IDs. The accepted
kind-selection sign reset was explicitly excluded as a defect. Bulk preview vs
submission mismatch, duplicate navigation pops and pagination races were traced
statically; no device reproduction is claimed.

No physical-device, process-death, release or performance tests were run during
this review. Device-test APK compilation does not establish runtime behavior.
The removed financial regression cases and unsaved-editor process-death coverage
remain verification gaps. Next: fix the review findings with focused regression
coverage before completing the redesign's device acceptance or starting 2.7.

## Review fixes and feature structure — September 27, 2026

Implemented the authorized review fixes in the existing working tree. Preserved
corrected foreign allocations across opening, metadata and sign edits; relevant
amount/item/order changes still recalculate. Leaving Transfer clears raw and
parsed received values, and reselecting a kind retains compatible categories.
Bulk preview and submission now use the same visible rows, with selection cleared
when query scope changes. Editor saves have one route completion owner; other
form completions carry their originating back-stack entry ID. Paging reserves
requests before launching, cancels old queries, rejects stale generations/cursors
and merges IDs. Search input has a 200 ms debounce.

Split screens into navigation, transactions/editor/list/detail, accounts, setup,
settings, overview, templates and shared components. Extracted editor/list/account
state owners and injected domain repository interfaces. Moved pure editor input
and transformations into `domain/editor`. Separated Room entities, DAO, migrations,
reader, templates and error mapping. Retained the existing database qualified
name/schema history and schema version 7. Batched aggregate relations inside a
read transaction; a populated page now uses five SELECTs instead of four child
queries per entry. Removed redundant posted/trash page reads from book refresh.
Unexpected implementation failures have a separate typed result and sanitized
exception-class-only logging. No financial exception payloads are logged.

Restored fourteen financial regressions for retained features, including populated
migrations from every version 1–6, exact large integers, interrupted migration
retry, cancellation/overflow rollback, currency locks, corruption byte retention,
reference detachment, Unicode/filter search, stale bulk atomicity, tied pagination,
and corrected foreign allocation round trips. Added seven deterministic host
state tests for delayed save/refresh, retry, discard, paging and selection. The
UI suite now distinguishes rotation, restored navigation with a fresh ViewModel,
and actual process restart; the latter confirms a different PID and absence of
the abandoned editor from posted/incomplete storage. Explicit incomplete records
still survive process restart.

Verification:

- `./gradlew spotlessApply` passed. `./gradlew verify` passed after final source
  changes: 20 domain tests, seven application state tests and ten host-script
  tests. Android lint: zero errors, app 50 warnings plus one hint, persistence
  six warnings. Remaining warnings include existing resource/localization and
  dependency/API advisories; dependency upgrades were not included in this fix.
- `./gradlew verify :app:assembleDebug :app:assembleDebugAndroidTest
  :persistence:assembleDebugAndroidTest` passed. Subsequent test-only rebuilds
  and final verification also passed, using default Gradle caches with supported
  access escalation.
- Rediscovered the sole connected OnePlus 6T/API 35 using `adb devices -l` and
  explicitly selected it. Installed debug app and instrumentation APKs with `-r`.
  No app data was cleared; other apps were untouched.
- `node scripts/run-financial-tests.mjs <discovered-serial> --ui` passed all 38:
  seven financial storage, fourteen regressions, six setup, seven UI and four
  separate-process steps. Sanitized summary: ignored
  `.local/financial-evidence/instrumentation.json`.
- During verification, corrected a Kotlin/JUnit non-void test signature, an
  incorrect Compose test import and an assertion that ran before asynchronous
  navigation completed. Also made UI fixtures tolerate pre-existing synthetic
  incomplete records and assert that discard returns to the posted detail.
  Final full device rerun passed.
- `git diff --check` and `git diff --cached --check` passed. New source/document
  whitespace was checked separately. Existing staging and unrelated local files
  remain preserved.

The initial sandboxed Node runner could not connect through ADB and reported
`ADB command failed or timed out` at its device stage. Supported escalation
allowed it to use the existing ADB server; no environment/cache workaround was
introduced.

Remaining scope: substring search still scans candidate rows; Phase 2.9 retains
its 50,000-record benchmark/index work. Full compact/wide, theme, large-text,
keyboard/inset/accessibility and release acceptance remain pending. Force-stop
plus fresh-ViewModel recreation tests do not prove Android low-memory task
restoration. Reports/rates, backup/restore and signing recovery gates remain
unchanged. Phase 2 is not marked complete.

No commit was created: the checkout already contains staged documentation moves
and substantial unrelated/earlier Kotlin and configuration changes. Its check-only
hook requires all relevant working files to match the index. Kept that staging
intact rather than mixing those changes into this milestone or bypassing the hook.
The [structure guide](../project-structure.md) and
[review follow-up](../reviews/2026-09-27-codebase-review.md#implementation-follow-up)
record the final boundaries and regression mapping. Next: finish the editor visual
acceptance matrix before starting 2.7.

## Save refresh and balance query performance — September 27, 2026

The user authorized fixing the first two findings of a read-only performance
inspection. No schema, financial semantics or Gradle module changed.

- Account balances: `RoomAccounts.list()` now reads one grouped
  `SUM(postings)` over posted entries instead of one `balance(accountId)` query
  per account. Accounts without postings report 0. SQLite still rejects overflow
  in the grouped `SUM`; post/trash/restore keep their per-account overflow checks.
- Save refresh: `BookViewModel.reload` takes the book parts a mutation can
  change (settings, accounts, categories, tags, templates, incomplete, totals,
  ledger). Initial load and Retry still load everything, as does any reload
  before the first complete load. Tags, templates, display currency, account
  metadata and incomplete deletion no longer reload balances, totals or the
  ledger. Posting/trash/restore/purge/adjust/opening balances reload accounts,
  totals and ledger; bulk categorization reloads the ledger; category saves
  reload categories and the ledger because reparenting changes filter matches.
- Ledger refresh: `TransactionListStateOwner.refresh` keeps current rows
  visible while it reloads, requests the depth already loaded (rounded up to
  50, at most 200 per request) and keeps a selection only while its entry token
  and allocation are unchanged. A failed refresh keeps rows and reports the
  error. Filter changes still reset the list and selection.

Verification:

- `./gradlew spotlessApply` passed. `./gradlew :app:testDebugUnitTest` passed
  15 application state tests, including new reload-scope, depth-preserving
  refresh, selection-pruning and failed-refresh tests.
- `./gradlew verify :app:assembleDebug :app:assembleDebugAndroidTest
  :persistence:assembleDebugAndroidTest` passed.
- Rediscovered the sole OnePlus 6T/API 35 with `adb devices -l`, selected it
  explicitly and installed the debug app and both instrumentation APKs with `-r`.
  No app data was cleared. `node scripts/run-financial-tests.mjs <serial> --ui`
  passed all 39 checks. The runner now expects seven setup tests, adding a
  grouped-balance regression covering transfers, trash exclusion and unused
  accounts.

Not measured: no before/after timing on the device; the 50,000-entry benchmark
remains 2.9 work. No commit was created for the same reason as the previous
entry: the touched files belong to the existing uncommitted restructuring.

## Sentry crash diagnostics — September 27, 2026

The user chose to send diagnostics to their Sentry-compatible endpoint
`events.mira.tt` (project 4) and delegated feature selection. This is a
cross-phase decision recorded in the master plan.

- Added `io.sentry:sentry-android-core:8.58.0` to the app only. Initialization
  is manual (`io.sentry.auto-init=false`) in `KocheniteApplication`. Enabled
  features: uncaught crashes, ANRs, release-health sessions, and app-start and
  frame performance (`tracesSampleRate = 1.0`). Environment is the build type;
  release is `applicationId@versionName+versionCode`.
- Privacy: no default PII, server name, screenshots, view hierarchy,
  user-interaction breadcrumbs/tracing or replay module. `beforeSend`,
  `beforeSendTransaction` and `beforeBreadcrumb` remove messages, exception
  values, extras and request data. A JVM test covers the scrubber.
- Persistence exposes `StorageDiagnostics.reporter`. `storageResult` reports
  faults that become `RecoveryRequired` or `UnexpectedFailure` without changing
  the ledger result; the app forwards them to Sentry.
- DSN in `gradle.properties` as `kocheniteSentryDsn`; release always embeds it,
  debug only with `-PkocheniteSentryDebug=true`. Added `app/proguard-rules.pro`
  (`-dontobfuscate`, keep source/line attributes) so release traces are readable
  without an auth token for mapping uploads. Added `INTERNET` permission.

Verification:

- `./gradlew spotlessApply`, `:app:testDebugUnitTest` (16 tests) and
  `./gradlew verify` passed.
- `./gradlew :app:assembleRelease` fails before this change's code runs: the
  working tree enables `org.gradle.configuration-cache`, and the existing
  `requireReleaseSigning` task captures script objects. With
  `--no-configuration-cache` the signed release APK built. Its merged manifest
  adds only `INTERNET`; native libraries are unchanged (four
  `libandroidx.graphics.path.so`).
- `node scripts/check-apk.mjs <release apk> "$ANDROID_HOME" release` now fails
  with `Unexpected platform permission` for `INTERNET`. Editing that check was
  blocked by the session's permission classifier and is left to the user.
- On the OnePlus 6T/API 35: built debug with `-PkocheniteSentryDebug=true`,
  installed with `-r` on the debug identity, launched it and backgrounded it.
  Logcat showed `io.sentry.auto-init read: false`, no crash and no Sentry
  errors. A synthetic host envelope (environment `setup-check`, message
  "Kochenite Sentry setup check (synthetic)") returned HTTP 200 with id
  `6a0d132d85d747ec84013e460e7a5920`. Receipt in the Sentry UI was not checked
  from this host.
- Reinstalled the normal debug build (empty DSN confirmed in generated
  `BuildConfig`) and reran `node scripts/run-financial-tests.mjs <serial> --ui`:
  all 39 passed. No app data was cleared.

Open: the APK permission check, the release configuration-cache failure, and
whether to add an in-app diagnostics opt-out. No commit, for the same reason as
the previous entries.

## System Back from a changed editor stranded navigation — September 27, 2026

The user reported the debug app ignoring every button after leaving an edited
transaction. Logcat showed edge-swipe system Back gestures from the editor
immediately before the freeze. Reproduced on the OnePlus 6T with
`KEYCODE_BACK`: Back left the changed editor without a discard prompt, and tab
taps then did nothing.

Cause: `BookScreen` registered the editor `BackHandler` before `NavHost`, whose
own back callback is registered later and therefore has priority. System Back
popped `EntryEditorRoute` without `closeEditor`, leaving changed editor state in
memory. Every later tab, Add or top-bar Back went through `closeEditor`, which
set `confirmDiscard` for a dialog owned by the no-longer-displayed editor.

Fix: register the editor `BackHandler` inside the `EntryEditorRoute` destination.
Added `BookUiDeviceTest.systemBackFromChangedEditorAsksBeforeLeavingAndKeepsNavigationWorking`
(dispatcher Back, keep editing, Back, discard, then a tab still navigates); the
runner now expects eight UI tests. The new test failed against the old handler
placement ("Discard changes?" not displayed) and passes with the fix.

Verification: `./gradlew verify` and all APK/instrumentation builds passed;
`node scripts/run-financial-tests.mjs <serial> --ui` passed 40/40; a manual
`KEYCODE_BACK` replay shows the discard dialog. The frozen debug process was
force-stopped for recovery; no app data was cleared.

Remaining: the top-level `BackHandler(enabled = state.saving)` has the same
ordering weakness, so system Back can still leave a form during a save (the
save completes; only the guard is ineffective). Tests previously used only the
top-bar Back button.

## Navigation and Back consistency — September 27, 2026

The user asked to fix all navigation/Back problems found in the navigation
review and the freeze investigation.

- Save guard: `BookScreen` composes a `BackHandler` only while `state.saving`,
  so it is the newest callback and outranks `NavHost` and destination handlers.
  The previous always-registered guard was outranked and ineffective.
- Editor opening: incomplete, template, edit, copy and linked-refund paths no
  longer call `nav.navigate` directly. `TransactionEditorStateOwner.open`
  ignores requests while an editor exists or a load is pending, and navigates
  through `openEditor(origin)`, which refuses when saving or when the origin
  back-stack entry is no longer current. A refused navigation drops the loaded
  editor, so editor state never exists without its route. `navigate()` now
  creates a new editor inside `push()`, including after closing another editor.
- Stranded-editor recovery: if editor state ever lacks its route, `BookScreen`
  navigates back to it so the discard dialog is visible. An editor route whose
  id does not match the live editor pops itself.
- Top-bar Back: visibility is computed in the `BookScreen` body from the
  current entry. It was read inside the top-bar lambda, which Compose can skip,
  leaving a stale Back button (seen on the frozen Overview).
- Template save loads its entry when restored without detail state.
- Enabled `android:enableOnBackInvokedCallback` so API 33–35 devices use the
  same predictive back path that targetSdk 36 gets by default on Android 16.
  `NavController.currentBackStack` was avoided because lint rejects it as a
  restricted API.

Verification: `./gradlew spotlessApply verify` passed; app JVM tests are now 18
(two new editor-opening tests: repeated requests load/navigate once; refused
navigation leaves no editor). New device test
`systemBackLeavesUnchangedEditorAndTopBackMatchesTheBackStack`; runner expects
nine UI tests. `node scripts/run-financial-tests.mjs <serial> --ui` passed
41/41. Manual `KEYCODE_BACK` replay: changed editor shows the discard dialog,
Back on the dialog keeps editing, Discard returns and tabs navigate.

Limitation: an `adb shell input swipe` edge swipe was not recognized as a system
Back gesture, so real edge-swipe/predictive animation behavior still needs a
hand check on the phone. Forms other than the transaction editor still have no
unsaved-change confirmation; that is a product choice, not addressed here.

## Commit of September 26–27 work — September 29, 2026

The user asked agents to commit verified work when appropriate and to record
that in `AGENTS.md`. `AGENTS.md` now authorizes commits of verified increments
without asking, and says to commit before unrelated work and at session end.

Verification: `./gradlew verify` passed (80 of 85 tasks up to date). No
source, test or script file changed after the navigation entry above, whose
`node scripts/run-financial-tests.mjs <serial> --ui` run passed 41/41. Device
tests were not rerun this session.

Commits: the plan/worklog move to `docs/` with the `AGENTS.md` policy change,
then all Sept 26–27 work in one commit: Phase 1 proof retirement, feature
restructuring, review fixes, Sentry diagnostics, performance and navigation
fixes. Those changes were verified only together, not as separate
intermediate states.

Devices: `adb devices -l` now also lists a Pixel 8 Pro (husky) besides the
reference OnePlus 6T; it is not yet part of the recorded coverage. Next action
is unchanged: the editor visual acceptance matrix.

## Release build and APK check fixes — September 30, 2026

Fixed the two open build issues from the Sentry entry before visual acceptance.

- `app/build.gradle.kts`: `requireReleaseSigning` now captures the signing
  properties file and resolved keystore as plain `File` values at configuration
  time. Its `doLast` previously referenced script members and `Project.file`,
  which the configuration cache cannot serialize.
- `scripts/check-apk.mjs`: allows exactly `android.permission.INTERNET` (crash
  diagnostics) and reports any other platform permission by name.
- `docs/gradle-build.md`: the APK checker and network-permission statements
  now reflect the diagnostics permission.

Verification:

- Reproduced first: `./gradlew :app:assembleRelease` failed with "cannot
  serialize Gradle script object references" for `:app:requireReleaseSigning`.
- After the fix, `./gradlew :app:assembleRelease :app:assembleDebug` passed
  and stored the cache; a repeat `:app:assembleRelease` reported "Reusing
  configuration cache." and still executed `:app:requireReleaseSigning`.
- `node scripts/check-apk.mjs` passed for both the release and debug APKs
  (signature, 16 KB alignment, debug isolation). A synthetic badging sample
  confirmed `CAMERA` is still rejected while `INTERNET` is allowed.
- `./gradlew verify` passed.

Not checked: the missing-signing failure path was not exercised, to avoid
moving the release keystore. No app code changed, so device tests were not
rerun.

## Editor visual acceptance — September 30, 2026

Ran the editor visual acceptance matrix on the OnePlus 6T/API 35 debug build
(synthetic data only, installed with `-r`). Screenshots were checked by eye, and
touch targets and accessibility labels were read from `uiautomator dump`. The
phone's settings were changed temporarily and then restored: dark mode, font
scale 1.0/1.5/2.0, locked landscape, and `wm density 300` for a 1248dp-wide
window. Afterwards the phone read font 1.0, night mode no, physical density 450,
auto-rotate on.

Passed without changes: compact portrait in light and dark (recreation after
the theme change keeps the editor's input); Expense and Transfer layouts,
including cross-currency received currency; account sheet in dark; keyboard
with the focused field and Save footer visible at font scale 2.0; Back with the
keyboard open only hides it; Back from Items returns to the form, and Back on a
changed form asks before discarding; rotation keeps typed input; system-bar
and cutout insets; all editor controls are at least 48dp. The Overview
balance and incomplete count were unchanged after discarding.

Defects found and fixed:

- The sign toggle announced only "−"/"+". It now announces "Make amount
  positive/negative" (the string existed but was unused); removed the
  commented-out icon code.
- TalkBack read the "›" glyphs inside editor rows; they are now hidden from
  accessibility.
- An empty account row showed "Select an account to see its details"; it now
  shows "Choose account".
- At font scale 2.0, the Expense/Income/Transfer tabs broke mid-word and
  "From receipt" wrapped into three lines. Tab labels now stay on one line and
  shrink to fit only when needed. The date/receipt row wraps as whole buttons.
- On wide windows, the editor, Items screen and Save button stretched across
  the full width, with the amount far from its label. They are now capped at
  640dp and centred. The date/time dialog's 560dp cap had no effect because it
  came after `fillMaxWidth`.
- The item sheet's hard-coded "Amount" is now a string resource; "1 items" is
  now a plural.

Verification: `./gradlew spotlessApply verify :app:assembleDebug
:app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest` passed.
The fixed cases were rechecked on the phone: font 2.0 dark, wide 1248dp
editor, and date dialog width. After installing both test APKs,
`node scripts/run-financial-tests.mjs <serial> --ui` passed 41/41. No app data
was cleared.

Limitations and open items:

- TalkBack was not run live; labels were checked through the uiautomator tree
  and the code.
- The wide layout was checked only with a density override on the phone. No
  tablet or foldable was used, and the Pixel 8 Pro was not connected.
- Real edge-swipe and predictive Back animations still need a hand check.
- Not changed (product/design choices): dates show as ISO text, although the
  plan calls for locale-aware formatting; the app uses default Material purple,
  although the plan calls for teal accents; "Attachment" and a disabled
  "From receipt" remain inert placeholders.
- In compact landscape the form area is short (it scrolls).
- At font 2.0 each tab label sizes itself, so labels can differ slightly in
  size.
- The last Back left the app, and the phone returned to the previous
  (unrelated) task. That screenshot was deleted without being viewed.

## Material 3 redesign in Figma — September 30, 2026

The user asked for a complete app redesign in Figma that keeps every feature
and follows Material 3 practice. File:
<https://www.figma.com/design/0nD5HHluRn8UPxUbjJrK0n> (the user's Figma drafts).
This is design only; no app code changed.

What the file contains:

- Foundations: local variables named after Compose `colorScheme` roles
  (teal seed #006A6A) with Light and Dark modes; finance colours (income,
  caution) and six chart colours; the M3 type scale in Roboto; shape, spacing
  and elevation tokens. The official Material 3 Design Kit was available, but
  its components are bound to its purple baseline variables, so a local system
  was used to get the planned teal accents.
- Components: 61 Material icons and 27 components with properties (app bars,
  navigation bar and rail, FAB, buttons, chips, segmented buttons, text fields,
  list item, ledger row, notice, dialog, sheet handle, snackbar and others).
- Screens · Compact: 45 phone frames and a dialog sheet in 11 flows, with a
  design note under each frame: setup and storage states; Overview; Transactions
  (posted, incomplete, trash, overflow menu); search, filter sheet with
  validation, bulk categorize; detail, trash, permanent-delete dialog, save as
  template; the editor (expense, foreign purchase with future-date error,
  transfer, completing an incomplete entry); items with match/mismatch and undo;
  item sheet; date and time picker; currency, tags, related and notes sheets;
  editor dialogs; accounts, account forms, balance adjustment, groups and
  order; Reports and its empty state; Settings, categories, tags, templates.
- Screens · Dark: eight key screens with the colour collection set to Dark.
- Screens · Expanded: 1280×800 navigation rail with list-detail layouts for
  Transactions and Accounts, and a two-column Overview.

Design choices that differ from the current app (all open to the user's
decision before implementation):

- Status filters become chips under an M3 search bar; Templates and Bulk
  categorize move into the ⋮ menu.
- Numeric "Order (lower first)" fields become a drag-to-reorder "Groups &
  order" screen with accessible Move up/Move down actions.
- Dates use locale formatting and a combined date/time picker instead of ISO
  text.
- Settings reserves disabled 2.8 backup/restore rows, and Reports shows a
  category breakdown with a table view. Both are placeholders for 2.7/2.8
  scope and are not built yet.

Verification: every section was checked with Figma screenshots. Problems found
and fixed during the build: binding a variable dropped paint opacity (disabled
states now use layer opacity), and some cloned instances kept a black fallback
colour that could render as a black strip (all pages were audited and 14 paints
repaired). Font use was checked as Roboto throughout. No app build was needed
for this documentation-only change.

Limitations: dark and expanded layouts cover key screens, not every screen.
Tabular figures (`tnum`) are specified in notes because Figma text styles here
don't carry that feature. Motion and predictive Back are not designed. The
file thumbnail could not be set through the API.

### Redesign revision from user review — September 30, 2026

The user reviewed the Figma file and requested nine changes; all were applied
in the same file:

- Setup, New account and Balance adjustment now use the editor's amount
  control (a new "Amount input" component with a leading −/+ button).
- Amounts show currency symbols (¥612,300, −$16.50) instead of trailing
  codes; currency pickers and settings show "¥ JPY". Codes remain in account
  names, account descriptions and the currency pill.
- "Opening" is no longer a separate kind: opening balances are adjustments
  (the filter kind chips are now Expense, Income, Transfer, Adjustment).
- Transaction rows were rebuilt as a new "Ledger row" component following
  the user's Bluecoins reference: an icon and a 3×2 grid (title — merchant
  or other-side amount in tertiary — account amount; category — account and
  running balance), plus an optional note line. Transfers show one row per
  side. The old row component was removed.
- Editor: the currency pill has no dropdown arrow; helper sentences were
  removed; the "More details" group is gone and all fields sit in one list;
  the note is an inline field with an ✕ clear button, so the separate Notes
  sheet was removed.

Dark and tablet frames were regenerated from the updated phone screens.
Checked with Figma screenshots of the setup, overview, transactions, editor,
detail, accounts and tablet frames. Running balances in the rows are a
display requirement that the current list code does not compute yet.

### Second Figma review round — September 30, 2026

The user left six comments in Figma and pasted them into the session, because
the connected account's View seat can't read comments or annotations. Changes:

- Right-side spacing: the Items sum / Amount / Difference row (both Items
  screens) and Overview's income/spending/net-flow row now spread their columns
  and right-align the last value. The Reports category rows drop the chevron
  and fixed-width value box; the whole row stays the drill-down target.
- Setup and New account no longer have a separate currency field; the account
  currency is picked from the opening-balance amount control's currency pill,
  as in the editor.
- The currency sheet lists plain codes and names without symbols.
- Account · Edit shows the balance in the shared amount control with the
  currency pill disabled (locked currency); the separate currency field and
  the balance card were removed. Editing the amount shows a note explaining
  that the difference is saved as a balance adjustment, plus an optional
  adjustment note. The separate Balance adjustment screen was replaced by
  this "balance changed" state.

Dark frames and the tablet Accounts and Overview layouts were regenerated or
patched from the updated phone screens. Checked with Figma screenshots of
setup, the currency sheet, both Items screens, Reports and the Accounts
section.
