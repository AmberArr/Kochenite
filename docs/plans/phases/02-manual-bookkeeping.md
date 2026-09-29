# Phase 2 — Reliable Manual Bookkeeping

Status: in progress. Synthetic data only until milestone 2.9 passes.
Shared decisions: [master plan](../PLAN.md). Evidence: [worklog](../../worklogs/02-manual-bookkeeping.md).
Financial equations/examples: [financial rules](../../../docs/financial-rules.md).

## Delivery and verification

Keep app, pure Kotlin domain and persistence modules. Use a separate financial
Room database; add tables when needed, export schemas and test every migration.
Use focused query repositories and one atomic posting service; no revision tables.
All monetary operations detect overflow. All extraction and incomplete records
remain non-authoritative until explicit confirmation. Preserve existing files on
storage failures and expose typed validation/conflict/temporary/recovery results.

Complete each milestone's exit criteria before implementing the next; make focused
verified commits and keep dated results only in the worklog.

## 2.0 — Decisions and examples

- Record accepted scope and architecture in the master plan, this plan and worklog.
- Document ordinary expenses, signed balances/adjustments, lending, same/cross-currency
  transfers, foreign purchases, incomplete entries, refunds and splits with explicit
  postings, allocations, resulting balances and report contributions.
- Define recognition-draft and manual posted/edit/incomplete/trash transitions separately,
  along with the posting equations.
- Exit: examples are explicit and the kernel has no unresolved product decisions.

## 2.1 — Financial kernel and storage

- Domain money/local date-time/kinds/validation, exact arithmetic and overflow checks.
- Separate Room schema, preserving initialization and indexed bounded queries.
- Direct atomic posting with stable submission identity, expected posted-record token,
  duplicate protection and stale edit rejection without history. Persist only explicit
  incomplete records for manual entry; completing one consumes it atomically.
- Exit: JVM/device tests prove exactness, overflow, rollback, repeat submission protection,
  stale edits/incomplete tokens, reopen persistence and migration preservation.

## 2.2 — Setup, navigation, accounts and categories

- Offline setup, explicit account currency, changeable dashboard currency; four typed
  destinations (Overview/Transactions/Accounts/Reports), settings and persistent Add.
- Account groups/create/edit/order/archive/net-worth inclusion; signed opening and
  adjustment postings; optional metadata type; currency permanently locks on posting.
- Optional editable starter income/expense categories, two levels, archive, separate
  Uncategorized income/expense. Direct parent allocations allowed, rollup counts once.
- Exit: positive/zero/negative opening balances; saved navigation survives recreation;
  account/category editing cannot invalidate historical references.

### 2.2 implementation sequence

1. Add focused account/category/settings repositories and schema v3 for groups,
   ordering, optional account type and singleton display settings. Test v2 migration.
2. Create accounts and opening postings atomically through the posting boundary;
   enforce permanent currency locks and safe two-level category/reference edits.
3. Introduce serialized ID routes with Navigation Compose and ViewModel-owned
   transient editor state (never transaction input in saved state), offline setup and
   the four primary destinations. Reuse the
   existing palette and 48dp controls; use rail/list-detail on wider windows.
4. Add setup/account/category instrumentation including recreation and signed
   balances, using the financial persistence test package and synthetic fixtures.

## 2.3 — Basic entry, editing and trash

- Compact Expense/Income editor with title, date/time, amount/currency, account, category,
  optional rows and one Save transaction action.
- Ordinary editor and item input stays in memory. Rotation retains it while the ViewModel
  survives; process termination loses it. Changed Back asks for discard; unchanged Back exits.
- Save posts directly in one transaction using a stable submission identity and expected
  posted-record token. Failure, validation errors and stale edits leave the original effective
  and retain the form. No ordinary editor autosave, recovery, Drafts tab or draft-status text.
- Posted details, copy and template actions open read-only initialized in-memory forms; manual
  trash/restore/confirmed permanent purge remain token-checked. Future dates fail on Save.
- Exit: create/edit/trash/restore affects totals exactly once; duplicate submissions and stale
  edits are rejected; changed Back, rotation retention and process-death loss are verified.

## 2.4 — Transfers, merchant context, incomplete entries and refunds

- Same-currency equal principal/cross-currency actual amounts; optional merchant context.
- Only a genuinely missing actual account amount or transfer side can be explicitly saved
  under Incomplete after validating all other supplied fields. Such records have concurrency
  tokens and never affect balances or reports; unknown transfer sides leave both sides
  non-authoritative. Fees/interest are separate expenses.
- Editing an incomplete record is transient until Save; leaving retains only its previously
  saved state. Completing consumes it atomically with the posted transaction. Posted edits
  cannot be saved incomplete.
- Expense credits (standalone or linked by general reference), initialized from
  selected allocations; positive signs offset spending in their own period.
- Exit: lending/repayment exclude spending; merchant amounts never affect authoritative
  totals; completion posts once; refunds reduce spending on their own recorded date.

## 2.5 — Splits and inclusive receipt lines

- Exact category splits and receipt descriptions/final inclusive totals/categories. Item
  editing is an in-memory child screen and item order persists only with parent Save. Show
  reconciliation; foreign proportional account allocations use deterministic
  largest-remainder rounding and review/correction; safe line/allocation edit/removal.
- The parent + Split transaction action opens the item list without creating an item; + Add item
  is the explicit creation action.
- Exit: simple/split/itemized totals reconcile to actual charge, no stale allocations;
  no inferred tax or automatic balancing difference, no tax/discount/coupon/points shortcut.

## 2.6 — Search, tags, copying, templates and bulk categories

- Indexed bounded Unicode search in merchant/notes/receipt descriptions; date/account/
  category-descendant/tag/kind/status/currency-specific amount filters.
- Flat tags rename/archive; copy opens a new unsaved editor dated now with no identity/trash/reference link.
- Templates with selected fields/optional amounts/splits; ID references with missing or
  archived corrections required. Allocation-row selection/preview/atomic categories.
- Exit: expected search/filter results, templates never post, bulk rejects stale sets
  without partial writes and changes only selected categories, preserving all totals.

### 2.6 implementation sequence

1. Add schema v5 for flat tags, entry-tag links and templates; migrate v4 data.
   Extend incomplete-record and posted aggregates with tag IDs while keeping old JSON readable.
2. Add bounded database search over posted/trash records with date, account,
   category descendants, tag, kind, status and selected-currency amount filters.
   Include merchant/payee, notes and receipt descriptions in text matching.
3. Add tag editing/archival, copy to a new unsaved today-dated editor, and template
   creation/application that requires correction of archived ID references.
4. Add allocation-row bulk selection and preview. Compare selected entry tokens
   inside one write transaction; update only category IDs and reject any stale row.
5. Verify migration, exact filters, Unicode text, transient copy behavior, template safety,
   atomic bulk rollback and a device UI journey before closing the milestone.

## 2.7 — Reports and current estimates

- Overview balances/current net worth/month income/spending/net flow; Reports date-range
  income/expense/category totals/account balances/current worth, currency separated.
- Transaction drilldown retains range/filter state; calendar months (budget starts deferred).
- Replaceable Frankfurter HTTPS provider parses decimal numeric text; common-base cache
  24h after successful fetch; explicit refresh, provider date and fetch time separate;
  manual pair overrides/reciprocals, stale cache retention and omitted-currency list.
- Exit: totals reconcile, transfers/adjustments/incomplete/trash excluded appropriately;
  rate/display-currency changes never alter financial records; test fresh/expired/
  missing/malformed/manual rates. No financial contents sent to provider.

## 2.8 — Plain SQLite backup and safe restore

- File-picker export of all financial Room data, successful-backup time and optional
  in-app reminder (default seven days, configurable/disableable).
- Pause mutations, checkpoint/close/stage main file,
  reopen active DB, validate snapshot, stream destination, success after output close.
- Restore private staging: reject unexpected/newer DB, migrate staging only, validate
  integrity/FKs/schema/financial invariants; preview then explicit replacement confirmation.
  Suspend edits, import application tables atomically, retain installed Room metadata,
  recheck before commit, reset editors/reload. No mandatory preliminary backup.
- Exit: fresh debug restore reproduces all data/relationships/incomplete records/templates/reports, exported
  WAL writes retained; bad files/schemas/FKs, low space and interrupted/cancelled imports
  preserve active data; completed commits reported; no permissions/secrets imported.
- Preserve unreadable active DB and surface recovery-required; no automatic salvage.

## 2.9 — Release validation and handoff

- Update device runners/APK checks, retain identity/signature/debug isolation/OS backup
  exclusions/native alignment; signed N→N+1 financial/incomplete upgrade and full journeys.
- Compact/wide, light/dark, large text, keyboard/system bars/accessibility; distinguish
  host rendering from device coverage. Benchmark 50,000 synthetic entries on reference
  phone: warm ordinary saves <300ms, indexed searches <500ms over repeated measurements.
- Document normal use/incomplete/estimates/SQLite backups/limitations.
- Exit: ./gradlew verify, migrations, physical-device/release journeys, reconciled reports,
  fresh restore and performance targets pass; off-machine signing-key backup/recovery
  recorded and no critical correctness/recovery blocker to real bookkeeping.

## Current status and next action

- 2.0: complete; [decision/example evidence](../../worklogs/02-manual-bookkeeping.md#milestone-20).
- 2.1: complete; [kernel/device evidence](../../worklogs/02-manual-bookkeeping.md#milestone-21--september-23-2026).
- 2.2: complete; [setup/device evidence](../../worklogs/02-manual-bookkeeping.md#milestone-22--september-23-2026).
- 2.3: the previous ordinary autosaved-editor workflow is superseded by the transient editor redesign below; correctness, device regression and visual acceptance checks pass; see the [visual acceptance evidence](../../worklogs/02-manual-bookkeeping.md#editor-visual-acceptance--september-30-2026).
- 2.4: complete; [transfer/refund evidence](../../worklogs/02-manual-bookkeeping.md#milestone-24--september-23-2026).
- 2.5: complete; [split/receipt evidence](../../worklogs/02-manual-bookkeeping.md#milestone-25--september-23-2026).
- 2.6: complete; [search/template/bulk evidence](../../worklogs/02-manual-bookkeeping.md#milestone-26--september-23-2026). 2.7–2.9: pending.
- Transaction UI/signed-entry update: previously verified; [verification evidence](../../worklogs/02-manual-bookkeeping.md#transaction-ui-implementation-verification--september-23-2026). The new editor lifecycle supersedes its ordinary draft behavior.
- Prerequisite for real use: off-machine signing-key backup and recovery check.
- Save refresh scoping and grouped account balances: verified; see the [performance fix evidence](../../worklogs/02-manual-bookkeeping.md#save-refresh-and-balance-query-performance--september-27-2026). Search scans, related-picker debounce and list recomposition costs remain open.
- Release builds work with the configuration cache and the APK check allows only the diagnostics `INTERNET` permission; see the [build fix evidence](../../worklogs/02-manual-bookkeeping.md#release-build-and-apk-check-fixes--september-30-2026).
- Open editor limitations: live TalkBack, real edge-swipe/predictive Back and a physical wide device remain unchecked; dates display as ISO text rather than locale-formatted; the theme uses default Material colours rather than the planned teal accents. See the [visual acceptance evidence](../../worklogs/02-manual-bookkeeping.md#editor-visual-acceptance--september-30-2026).
- Next: begin 2.7 reports and current-rate estimates.

## Transaction editor redesign

### Review fixes and internal structure

The user authorized the review fixes and internal restructuring on September 27.
Keep the existing Gradle modules and stored schema/financial semantics.

1. Preserve persisted allocations across initialization and metadata-only edits;
   clear transfer-only input on kind changes and retain compatible categories.
2. Give editor completion one navigation owner; extract editor and list state
   from application/setup state. Make pagination cancellable and cursor-checked;
   use one bulk selection snapshot for preview and submission.
3. Introduce injectable read/editor/template boundaries, split UI by feature,
   and separate database declarations, entities, DAOs and migrations. Batch
   aggregate reads and distinguish unexpected faults from damaged storage.
4. Restore financial failure/migration/search regression fixtures; add host state
   tests and a real unsaved-editor process restart journey. Run pinned host checks,
   instrumentation builds and available physical-device tests without clearing data.

Exit: each review finding has regression evidence, feature/state ownership is
explicit, no new Gradle module or destructive schema change is introduced, and
remaining device/performance limitations are recorded honestly in the worklog.

- Status: review correctness fixes and internal restructuring verified; 20 domain tests, seven application state tests, ten host-script tests and 38 physical-device checks pass. See the [review follow-up](../../reviews/2026-09-27-codebase-review.md#implementation-follow-up) and [worklog](../../worklogs/02-manual-bookkeeping.md#review-fixes-and-feature-structure--september-27-2026). Visual acceptance passed with recorded limitations; see the [visual acceptance evidence](../../worklogs/02-manual-bookkeeping.md#editor-visual-acceptance--september-30-2026).
- Deliverables: complete API/state audit; remove stale manual-draft navigation/status behavior; test v6→v7 retirement of ordinary saved editor rows while preserving explicit incomplete records; align master/phase/UI/financial-state documentation and worklog.
- Required checks: focused domain/storage tests; `./gradlew verify`; app and persistence instrumentation builds; explicit incomplete creation/update/delete/completion; stale/duplicate/failure/future-date checks; UI Back/discard, rotation retention and process-death loss.
- Visual acceptance: inspect incremental editor/item changes on the physical phone, then verify compact/wide layouts, light/dark themes, large text, keyboard/system insets, accessible controls and touch targets.
- Exit: all checks above pass, the debug app writes no ordinary transaction data before Save, the original posted transaction remains effective through all failed edit paths, and the updated behavior is recorded in the worklog.
- Current check: `./gradlew verify`, debug APK and both instrumentation builds pass. All 38 device checks pass on the reference OnePlus 6T/API 35, including populated migrations 1–6, rollback/corruption preservation, save/discard navigation, rotation, fresh-ViewModel route restoration, actual PID replacement with unsaved editor loss, and explicit incomplete survival. Force-stop checks do not establish low-memory OS task restoration. Text search still scans candidates; the 50,000-record benchmark/index work remains 2.9. Visual acceptance and a 41-check device rerun passed on September 30. Phase 2 is not complete.
