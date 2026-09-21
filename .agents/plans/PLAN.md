# Kochenite: Personal Finance Android App

Status: Phase 1 in progress. The user approved migration to Gradle after the bounded Toolchain proof. Phase 1 is not complete.

## Goal and Agreed Decisions

Build a native, offline-first bookkeeping app for personal use, delivered in usable phases. Financial records stay on the device. Bookkeeping requires no account or sign-in. Recognition produces editable drafts and never changes balances without confirmation.

Agreed choices:

- Native Kotlin and Jetpack Compose with Gradle Kotlin DSL. The user selected Gradle after reviewing the Kotlin Toolchain 0.12.2 proof and future iOS considerations. Keep domain logic independent of Android; actual iOS/Multiplatform targets remain deferred.
- A comprehensive everyday-finance product delivered in phases.
- Local financial storage, with portable backups. Direct Google Drive/OneDrive support can be discussed later.
- Configurable reporting currency during setup.
- Receipt product lines and category splits, not independent nested transactions.
- Photo capture, gallery images, and shared images for initial receipt recognition.
- Configurable notification sources, conservative default templates, and user-editable templates with previews.
- Compact modern ledger UI with light and dark themes.
- Generic CSV import/export; no Bluecoins-specific migration promise.
- The recognition demo is supplied in `receipt-ai-demo/a.js` and `receipt-ai-demo/prompt.txt`. It is a provider-integration reference, not a validated contract. Phase 3 retains deterministic fixtures and defines the provider contract then.
- A possible Supabase Edge Function gateway with Supabase Auth is a later deployment option. A user-supplied recognition gateway without app sign-in is also deferred for later discussion.
- Use Git and make focused commits after verified milestones. Preserve the user's chosen `master` branch.

## Environment and Working Conventions

Workspace: `/home/amber/codex_workspace/kochenite-v2`.

The project started without application code. The user initialized Git and configured `init.defaultBranch` to `master`. Existing `.agents/` skills are user-provided resources; do not commit or modify them incidentally.

This machine does not support KVM. Do not create or launch an Android emulator. Build on the host and deploy, inspect, and test on a remote physical phone using ADB.

At planning time, wireless ADB connected to a OnePlus 6T (`ONEPLUS_A6013`) running Android 15 / API 35. Its observed serial was `adb-a2480128-JKi84V._adb-tls-connect._tcp`; rediscover the current serial with `adb devices -l` in each session and select it explicitly. Do not assume the serial remains stable. Never clear or uninstall the user's Bluecoins app or change its records during reference inspection.

Use the checked-in Gradle 9.5.0 wrapper and Kotlin DSL build from the workspace root with default tool caches. AGP 9.3.1, Kotlin 2.4.10, KSP2 2.3.11 and JDK 21 are verified together. If permissions block a tool/cache path, report the exact error and request required access; do not redirect caches or repurpose environment variables. Keep generated outputs and caches ignored. The prior Toolchain build is preserved in commit `368ce38`.

Toolchain 0.12.2 built and installed the Compose, Room/KSP2/SQLCipher and signed/shrunk release probes. The [capability matrix](../../docs/toolchain-proof.md) records commands, integration costs and limits. The user explicitly approved migration to Gradle on September 22, 2026 (local date). Use the pinned Gradle wrapper, standard Android variants and instrumentation for remaining Phase 1 work.

Android CLI 1.0.16261425 and ADB work. Always pass `--no-metrics`. API 36 revision 2 and command-line tools 22.0 are installed; builds use tools 37.0.0 and pinned JDK 21. The user removed conflicting `ANDROID_SDK_HOME` and retained `ANDROID_USER_HOME`; no cache redirection was applied.

## Reference and Product Scope

Bluecoins 13.1.79 was inspected through its visible UI using ADB. Observed references included its dashboard, transactions, reminders, balance sheet, accounts, categories, labels, calendar, trash, currency setup, notification settings, data management, synchronization settings, and fingerprint protection. These establish workflow references, not a claim of exhaustive feature inspection or a requirement to copy its layout.

### Accounts and Transactions

- Support cash, bank, savings, credit cards, e-wallets, assets, loans, and receivables. Include account groups, opening balances, archival, reconciliation, and inclusion/exclusion from net worth.
- Each account has one fixed currency. Group separate currency accounts under one institution. Prevent changing an account's currency once it has postings.
- Support income, expenses, transfers, refunds, and explicit balance adjustments.
- Transactions include merchant/payee, date, account, categories, tags, notes, attachments, and cleared/reconciled status. Include copying, saved templates, search, filters, and bulk categorization.
- Transfers and credit-card repayments move balances without becoming income or expenses. Fees and interest are separate expenses.

### Currencies and Financial Correctness

- Choose the reporting currency during setup. Preserve original transaction currency, actual account-currency amount, and historical conversion snapshots.
- Cross-currency transfers record actual sent and received amounts, with fees recorded separately. Do not require nominal amounts in different currencies to sum to zero.
- Store finalized monetary amounts as integer minor units with currency metadata. Use decimal arithmetic for quantities, unit prices, and exchange rates. Never use floating-point arithmetic for money.
- Historical spending uses saved conversion snapshots. Current net worth uses separately dated valuation rates. Missing rates must remain visibly unresolved, not silently assumed.
- Manual exchange rates work offline. Keep an optional dated rate-provider adapter for later automatic suggestions.
- Account postings determine balances. Category allocations determine spending. Receipt lines explain allocations; they are not additional transactions.
- Refunds link to original purchases and reduce spending in the refund posting period. Balance adjustments do not silently appear as earned income or ordinary expenses.

### Receipt Subitems

- Track product descriptions, decimal quantities, unit prices, line amounts, discounts, tax, and category allocations.
- Support tax-inclusive receipts and receipt-level discounts, tax, tips, or other adjustments without counting included tax twice.
- Allocate shared adjustments deterministically and expose rounding residuals. Require item/allocation totals to reconcile before posting; permit an explicit user-reviewed adjustment for unexplained differences.
- Permit category splits without product-level detail. A simple transaction is the same model with a single allocation.

### Everyday Finance

- Monthly category budgets, optional rollover, and configurable month start.
- Recurring bills/income, reminders, subscription tracking, and a calendar. Generate reviewable occurrences with unique schedule/date identities so restarts cannot duplicate them.
- Loans, money lent/borrowed, reimbursable purchases, and partial repayments. Principal moves balances; interest is an expense. Record reimbursable portions against a receivable account when appropriate.
- Savings goals earmark funds without creating spending or changing actual account balances.
- Reports cover income versus expenses, categories, merchants, tags, account balances, net-worth history, budget progress, and upcoming obligations. Every total drills down to contributing records. Display original/reporting currencies and rate provenance.

### Import, Export, and Backup

- Generic CSV import includes column mapping, date/locale/currency selection, preview, validation, duplicate warnings, atomic batches, and undo.
- Export transactions and subitems separately as CSV. CSV is not the full backup format.
- Full portable backups include all relationships, settings needed to interpret records, attachments, schema version, and integrity checksums. Exclude credentials and device-bound keys.
- Encrypt portable backups using a user passphrase and maintained cryptographic libraries. Do not implement cryptographic primitives manually.
- Restore into temporary storage, validate and migrate, then replace active data only after successful checks and a pre-restore backup. Failed restores must leave the original data usable.
- Initially use Android Storage Access Framework for file export/import. Direct Drive/OneDrive integration and synchronization semantics are deferred.

## User Experience

- Five primary destinations: Overview, Transactions, Accounts, Inbox, and Reports. Overview exposes budgets, bills, and goals; settings contains categories, templates, backups, and recognition configuration.
- Persistent add action, amount-first transaction entry, recent selections, and autosaved drafts. Preserve filters, scroll positions, and unsaved work across navigation and process recreation.
- Material 3 with neutral light/dark surfaces, restrained teal accents, distinct chart colors, system typography, aligned monetary columns, and compact readable lists.
- Use accessible icons, 48dp touch targets, text scaling, TalkBack semantics, edge-to-edge insets, keyboard-safe forms, and predictable back navigation. Do not rely on color alone.
- Wider windows use navigation rails and list/detail layouts. Charts offer exact values and accessible textual/tabular alternatives.
- Recognition review shows the receipt alongside extracted fields/items, surfaces uncertainty and mismatches, and makes account/category correction efficient.

## Technical Architecture

- Separate the Android app, pure Kotlin finance domain, Android persistence/platform services, and recognition contract. Organize features within these modules initially; avoid a module per screen.
- Use ViewModels, StateFlow, coroutines, repository interfaces, and constructor injection with an explicit composition root.
- Use Room with KSP2, SQLCipher, DataStore for non-sensitive preferences, and encrypted private attachment files.
- Core entities include accounts, transaction aggregates, account postings, allocations, receipt items, exchange-rate snapshots, drafts, source events, attachments, schedules, budgets, and import batches. Use stable identifiers for references and deduplication.
- A single transactional posting service validates and writes each aggregate. Editing updates its postings and allocations atomically. Maintain revision history.
- Trash excludes records from calculations and supports restoration. Editing reconciled records requires confirmation and renewed reconciliation.
- Use CameraX for capture, Photo Picker/share intents for images, WorkManager for durable recognition/retry and maintenance, and NotificationListenerService for explicitly selected sources.
- Keep financial arithmetic and notification parsing independent of Android for JVM testing.
- Protect encryption keys with Android Keystore. Optional biometric/device-credential app lock protects the UI independently of background capture. Exclude financial data and credentials from automatic OS backup; background work must tolerate keys being unavailable before device unlock.
- Avoid receipt content, notification bodies, credentials, and financial records in diagnostic logs. Keep logs local unless the user deliberately exports sanitized diagnostics.
- Defaults: working name Kochenite, minimum Android API 26, initial compile/target API 36, English UI with externalized strings and locale-aware formatting. Support Unicode receipt content. Initial recognition fixtures cover English, Japanese, and Traditional Chinese; no specific receipt language was selected by the user.

## Recognition and Notification Interfaces

### Receipt Workflow and Local Service

1. Capture, select, or share images; rotate/crop/reorder them and group multiple images as one receipt.
2. Persist a local recognition request and send it to the configured development gateway.
3. Review the result against the images, reconcile totals, and choose accounts/categories.
4. Post only after explicit confirmation, or attach the receipt to an existing transaction.

Define `RecognitionGateway.recognize(request)` independently of hosting and model provider. The versioned request includes a request ID, images, and optional locale/currency hints. Results include merchant, date, currency, totals, items, adjustments, warnings, and available field evidence/confidence. Transfer decimal values as strings. Treat confidence as a review aid, not proof of correctness.

In Phase 3, build a small TypeScript/Node HTTP development service with `GET /health` and `POST /v1/recognize`. Start with deterministic fixture responses, a provider-adapter interface, a documented contract, and shared contract-test fixtures. Consult the supplied demo when designing the real provider integration; its protocol is not yet a validated contract. No recognition service is implemented in Phase 1.

Bind the service to localhost. Connect the physical phone using:

```sh
adb -s "$ANDROID_SERIAL" reverse tcp:8787 tcp:8787
```

Use `http://127.0.0.1:8787` on the phone through that tunnel. Allow localhost cleartext HTTP only in debug builds. Keep provider credentials in the service environment, never in the APK or Git.

Persist inputs/drafts across timeouts, offline periods, process death, and malformed responses. Bound image dimensions, count, and upload size. Retry transient failures with backoff; provide manual retry/cancel. Repeated responses must not create repeated ledger entries. Inconsistent recognition results require correction, not silent adjustment.

### Notification Templates and Capture

- Require explicit source-app selection and notification-access permission. Parse selected notifications locally.
- Supply conservative starter templates for purchase, debit, credit, and refund notifications. These are generic starting points, not promises of bank-specific coverage without examples.
- Provide a user template editor with captured/pasted samples, title/body matching, field mapping, locale rules, account/category defaults, and live extraction preview. Offer bounded advanced patterns using a safe regex engine.
- Make template priority explicit and testable. Ambiguous matches remain drafts requiring review rather than choosing a financial interpretation silently.
- Deduplicate repeated updates using source-event identity. Use amount/currency/merchant/time similarity only to suggest matches across notifications, receipts, and manual entries.
- Let the user merge drafts or attach evidence to an existing transaction without silently discarding legitimate identical purchases.
- Show capture status and handle permission revocation/reconnection. Retain only selected-source evidence; delete raw notification text after posting/dismissal by default.
- Do not promise historical capture or access to content hidden by Android or the source app. Recognition failures and capture gaps must remain visible.

### Future Gateway Hosting

Preserve the recognition contract for a possible Supabase Edge Function deployment with Supabase Auth, server-side secrets, quotas, and bounded data retention. Sign-in would apply to that recognition service, not local bookkeeping. Assess deployment limits and asynchronous processing when that phase is designed. User-owned gateway configuration/authentication and direct Drive/OneDrive support are deferred design tasks, not prerequisites for the local app.

## Implementation Phases and Exit Criteria

Phase 1 proof has started. Complete and verify each phase before moving on; commit coherent verified increments within phases.

### Phase 1: Foundation and Toolchain Proof

Status: proof and Gradle migration verified, including repeat clean builds and real instrumentation. Remaining foundation work is incomplete. See [Gradle commands](../../docs/gradle-build.md) and the [Phase 1 plan](phases/01-foundation.md).

- Initialize an isolated pinned Kotlin Toolchain 0.12.2 Compose proof with default caches and appropriate generated-output/secret ignores. Stop at four active hours or earlier decisive evidence and obtain the user's build-system choice.
- Prove Compose builds, Room generation and migrations, SQLCipher packaging, and deployment to the remote physical phone.
- Prove release APK creation/signing and upgrade continuity; keep signing credentials and keys outside Git. Check encrypted native-library packaging compatibility, including modern page-size requirements.
- Establish JVM tests and a repeatable physical-device journey runner. Verify Android instrumentation support early. If substantial unsupported build integration is required, explain the blocker to the user before considering a Gradle switch.
- Document build, test, explicit device selection, installation, log collection, screenshots, and local-service tunneling. Do not create an emulator.
- IDs: release `moe.frost.kochenite`, debug `moe.frost.kochenite.debug`; disposable `foundation-proof.db` stays separate from Phase 2 finance schema. Durable signing key is created only after the build decision, under ignored `.local/signing`.
- The proof uses `toolchain-probe.db` and temporary release ID `moe.frost.kochenite.debug.releaseproof`, preserving the permanent identity for its durable key. SQLCipher 4.17.0/SQLite 2.6.2 preserve API 36; SQLCipher 4.19.0 requires compile API 37. Reassess versions after the build choice without silently changing SDK requirements.
- Exit: all detailed Phase 1 criteria have evidence, including encrypted persistence/restart/atomicity/migration, signed release upgrade, repeatable device tests and static native checks. API 35/4 KB phone coverage does not establish other OS or 16 KB runtime support.

### Phase 2: Reliable Manual Bookkeeping

Status: not started.

- Deliver setup, accounts, categories, manual transactions, transfers, currencies, subitems, search, editing/trash, basic reports, and encrypted backup/restore.
- Establish the financial invariants before adding automation, including exact allocation totals and authoritative account postings.
- Exit: daily bookkeeping works entirely offline; backup restoration reproduces balances, relationships, and attachments.

### Phase 3: Receipt Capture

Status: not started.

- Deliver image acquisition, persistent drafts, fixture gateway, review/reconciliation, retries, and receipt-to-existing-transaction matching.
- Design the provider contract with reference to the supplied demo. Preserve deterministic fixture mode independently of provider integration; real AI accuracy remains unverified until a real provider is integrated.
- Exit: complete capture-to-posting journeys work over remote ADB, including error recovery, without changing ledger logic when the provider adapter is replaced.

### Phase 4: Notification Capture

Status: not started.

- Deliver source selection, starter templates, user template editing/testing, capture status, deduplication, and Inbox integration.
- Use a separate debug fixture app to emit synthetic payment notifications; do not depend on real financial activity for tests.
- Exit: template customization, repeated-notification handling, permission revocation/recovery, and manual confirmation all work on the physical phone.

### Phase 5: Everyday Finance

Status: not started.

- Add budgets, recurring schedules, reminders/calendar, reconciliation, debts/reimbursements, goals, generic CSV import/export, and expanded reports.
- Ship each feature with its financial invariants and migration coverage. Keep recurring occurrences reviewable.
- Exit: reports reconcile with underlying records; scheduling is idempotent; imports are previewable and reversible; debt and goal flows do not double-count spending.

### Phase 6: Daily-Use Hardening

Status: not started.

- Validate release shrinking, encrypted database compatibility, signing-key continuity, backup recovery, accessibility, permission denial, offline behavior, and long-term data performance.
- Produce a signed sideloadable APK and repeatable build/deploy/backup instructions.
- Exit: release-build daily-use journeys and restoration tests pass on the reference device, with outstanding limitations explicitly documented.

## Verification Strategy

- Finance: zero-, two-, and three-decimal currencies; fractional quantities; tax-inclusive/exclusive receipts; discounts/rounding; cross-currency transfers and fees; refunds; card repayments; stable historical rates; atomic edit/delete/restore; budget rollover; partial debt settlement.
- Extraction: ambiguous currencies/dates, unreadable images, inconsistent totals, duplicate notification updates, identical legitimate purchases, receipt/notification matching, template precedence, adversarial pattern inputs, timeout/retry/cancel, and process death around response persistence.
- Storage: every schema migration, interrupted writes, corrupt/truncated backups, incorrect passphrases, attachment checksums, low storage, failed restore without data loss, and successful restoration on a fresh app installation. Use only this app's test data for destructive recovery scenarios.
- Device: capture, review, posting, reporting, and restoration journeys via explicitly selected wireless ADB. Check rotation, large text, dark mode, keyboard/system-bar overlap, permission revocation, and background restrictions.
- Adaptive UI: use host rendering tests where supported for other sizes; clearly distinguish rendering checks from physical-device coverage. Do not claim untested phones/tablets were verified.
- Performance: benchmark synthetic 50,000-transaction data on the reference phone. Target ordinary saves below 300 ms and indexed searches below 500 ms after warm-up. Keep database work and image processing off the main thread.
- Git: inspect diffs and run checks appropriate to each change before committing. Stage only intended files, keep caches/secrets/test receipts out of commits, and do not push unless requested.

## Deferred Scope

The initial product is a personal sideloaded Android app with one local book containing many accounts. Public-store operations, shared households, securities trading, business accounting, direct bank integrations, Bluecoins-specific migration, PDFs/email ingestion, cloud synchronization, and production AI hosting are outside these implementation phases.

## New-Session Handoff

Read this file and the detailed Phase 1 plan. Gradle migration is verified; continue remaining foundation implementation using `./gradlew`. Do not mistake the synthetic proof for the foundation or start Phase 2. Use default caches and explicitly selected physical-device ADB. Update phase evidence and make focused Git commits. The recognition demo is supplied; fixture recognition and provider-contract design remain Phase 3 work.
