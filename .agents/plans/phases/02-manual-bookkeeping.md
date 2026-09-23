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
- Define draft/posted/edit/trash transitions and posting equations.
- Exit: examples are explicit and the kernel has no unresolved product decisions.

## 2.1 — Financial kernel and storage

- Domain money/local date-time/kinds/validation, exact arithmetic and overflow checks.
- Separate Room schema, preserving initialization and indexed bounded queries.
- Atomic posting, durable draft identity/consumption, stale edit rejection without history.
- Exit: JVM/device tests prove exactness, overflow, atomic rollback, repeat confirmation,
  stale edits, reopen persistence and migration preservation.

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
3. Introduce serialized ID routes with Navigation Compose, saved navigation/forms,
   ViewModel state, offline setup and the four primary destinations. Reuse the
   existing palette and 48dp controls; use rail/list-detail on wider windows.
4. Add setup/account/category instrumentation including recreation and signed
   balances. Preserve sample storage as a separate diagnostic foundation.

## 2.3 — Basic entry, drafts, editing and trash

- Amount-first expense/income with account, merchant/payee, date/time, category, notes.
- Durable autosave/resume/discard/explicit posting; accurate Saving/Saved, await pending
  saves on Back, visible failures retaining input; draft content in Room, UI state saved.
- Details, durable edit drafts, manual trash/restore/confirmed permanent purge; ledger
  filters for posted/draft/incomplete/trash. Future dates only as drafts.
- Exit: create/edit/trash/restore affects totals exactly once; acknowledged drafts survive
  recreation/process termination; failed post preserves draft and original ledger.

## 2.4 — Transfers, merchant context, incomplete entries and refunds

- Same-currency equal principal/cross-currency actual amounts; optional merchant context.
- Unknown actual amounts save incomplete with affected-view markers/counts; unknown
  transfer side leaves both sides incomplete. Fees/interest are separate expenses.
- Standalone/linked/partial refunds initialized from selected purchase allocations,
  later receipt items; category defaults/context only, different receiving account allowed.
- Exit: lending/repayment exclude spending; merchant amounts never affect authoritative
  totals; completion posts once; refunds reduce spending on their own recorded date.

## 2.5 — Splits and inclusive receipt lines

- Exact category splits and receipt descriptions/final inclusive totals/categories;
  visible reconciliation; foreign proportional account allocations with deterministic
  largest-remainder rounding and review/correction; safe line/allocation edit/removal.
- Exit: simple/split/itemized totals reconcile to actual charge, no stale allocations;
  no inferred tax or automatic balancing difference, no tax/discount/coupon/points shortcut.

## 2.6 — Search, tags, copying, templates and bulk categories

- Indexed bounded Unicode search in merchant/notes/receipt descriptions; date/account/
  category-descendant/tag/kind/status/currency-specific amount filters.
- Flat tags rename/archive; copy as new draft dated now with no identity/trash/refund link.
- Templates with selected fields/optional amounts/splits; ID references with missing or
  archived corrections required. Allocation-row selection/preview/atomic categories.
- Exit: expected search/filter results, templates never post, bulk rejects stale sets
  without partial writes and changes only selected categories, preserving all totals.

### 2.6 implementation sequence

1. Add schema v5 for flat tags, entry-tag links and templates; migrate v4 data.
   Extend draft and posted aggregates with tag IDs while keeping old JSON readable.
2. Add bounded database search over posted/trash records with date, account,
   category descendants, tag, kind, status and selected-currency amount filters.
   Include merchant/payee, notes and receipt descriptions in text matching.
3. Add tag editing/archival, copy to a new today-dated draft, and template
   creation/application that requires correction of archived ID references.
4. Add allocation-row bulk selection and preview. Compare selected entry tokens
   inside one write transaction; update only category IDs and reject any stale row.
5. Verify migration, exact filters, Unicode text, draft identity, template safety,
   atomic bulk rollback and a device UI journey before closing the milestone.

## 2.7 — Reports and current estimates

- Overview balances/current net worth/month income/spending/net flow; Reports date-range
  income/expense/category totals/account balances/current worth, currency separated.
- Transaction drilldown retains range/filter state; calendar months (budget starts deferred).
- Replaceable Frankfurter HTTPS provider parses decimal numeric text; common-base cache
  24h after successful fetch; explicit refresh, provider date and fetch time separate;
  manual pair overrides/reciprocals, stale cache retention and omitted-currency list.
- Exit: totals reconcile, transfers/adjustments/drafts/trash excluded appropriately;
  rate/display-currency changes never alter financial records; test fresh/expired/
  missing/malformed/manual rates. No financial contents sent to provider.

## 2.8 — Plain SQLite backup and safe restore

- File-picker export of all financial Room data, successful-backup time and optional
  in-app reminder (default seven days, configurable/disableable).
- Flush acknowledged draft writes, pause mutations, checkpoint/close/stage main file,
  reopen active DB, validate snapshot, stream destination, success after output close.
- Restore private staging: reject unexpected/newer DB, migrate staging only, validate
  integrity/FKs/schema/financial invariants; preview then explicit replacement confirmation.
  Suspend edits, import application tables atomically, retain installed Room metadata,
  recheck before commit, reset editors/reload. No mandatory preliminary backup.
- Exit: fresh debug restore reproduces all data/relationships/drafts/reports, exported
  WAL writes retained; bad files/schemas/FKs, low space and interrupted/cancelled imports
  preserve active data; completed commits reported; no permissions/secrets imported.
- Preserve unreadable active DB and surface recovery-required; no automatic salvage.

## 2.9 — Release validation and handoff

- Update device runners/APK checks, retain identity/signature/debug isolation/OS backup
  exclusions/native alignment; signed N→N+1 financial/draft upgrade and full journeys.
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
- 2.3: complete; [entry/device evidence](../../worklogs/02-manual-bookkeeping.md#milestone-23--september-23-2026).
- 2.4: complete; [transfer/refund evidence](../../worklogs/02-manual-bookkeeping.md#milestone-24--september-23-2026).
- 2.5: complete; [split/receipt evidence](../../worklogs/02-manual-bookkeeping.md#milestone-25--september-23-2026).
- 2.6: complete; [search/template/bulk evidence](../../worklogs/02-manual-bookkeeping.md#milestone-26--september-23-2026). 2.7–2.9: pending.
- Prerequisite for real use: off-machine signing-key backup and recovery check.
- Next: implement 2.7 authoritative reports and replaceable current-rate estimates,
  beginning with report drilldowns and rate-provider/cache contracts.
