# Phase 1: Foundation and Build-System Proof

Status: complete for the synthetic Phase 1 foundation. Gradle, plain Room storage,
device instrumentation, repeat clean builds and signed release upgrade are verified.
Coverage limits and the prerequisite signing backup before real use are recorded below.
Implementation history and verification results are in the
[Phase 1 worklog](../../worklogs/01-foundation.md).

## Scope and Decisions

Implement the user-approved Phase 1 plan. Kochenite uses release application ID
`moe.frost.kochenite`, debug ID `moe.frost.kochenite.debug`, minimum API 26 and
compile/target API 36. Synthetic data only. Shared architecture and scope remain
in [the master plan](../PLAN.md).

Current storage decision: ordinary Room/SQLite in private internal storage.
Database-level encryption, SQLCipher, passphrase generation/wrapping, database-key
recovery and encryption-specific tests are removed from scope. Android sandbox
and device encryption remain the storage protections. Preserve atomic writes,
migrations, non-destructive failure handling, backup/device-transfer exclusions,
and unlock-aware access to credential-protected storage. Release signing keys and
future encrypted exported backups are separate requirements and remain in scope.

Build-system decision: Gradle was selected after the bounded Toolchain proof.
The [capability matrix](../../../docs/toolchain-proof.md) records the tradeoffs;
proof execution history is kept in the worklog.

## Milestones and Implementation

1. Establish the selected Gradle build with pinned versions, debug/release identity
   and source/resource isolation, KSP schema export, instrumentation, shrinking,
   signing and native packaging checks. Preserve the build-system decision.
2. Establish app, pure Kotlin domain, Android persistence and
   platform, and unimplemented recognition boundary. Composition root/constructor
   injection, ViewModel/StateFlow, background database work. Sample list/add action,
   loading/empty/error states, externalized strings, light/dark, accessible targets
   and system-bar insets.
3. Disposable `foundation-proof.db`: stable IDs, integer minor units, currency
   code/scale, UTC timestamps; v2 adds nullable note. Narrow observable/atomic
   sample repository; ready/temporarily unavailable/recovery-required initialization
   using ordinary Room/SQLite in private internal storage. Unlock-aware access;
   preserve existing data on corruption, I/O or migration failures. No destructive
   migration or automatic database deletion/recreation as error recovery.
   Backup/device-transfer exclusions; no new permissions or database-key layer.
4. Explicit-serial bounded test runner, sanitized results and repeatable builds.
   Durable password-protected key under ignored `.local/signing`, restricted
   permissions, public fingerprint, encrypted backup/recovery instructions.
   Actual off-machine backup needs a user-controlled destination before real use.

## Exit Criteria

Completion evidence: [foundation verification](../../worklogs/01-foundation.md#final-foundation-evidence).
Subsequent tooling checks: [tooling verification](../../worklogs/01-foundation.md#post-foundation-tooling).

- [x] Build-system choice recorded and clean-output debug/release builds repeat.
- [x] Debug and signed release run on the reference phone.
- [x] Domain/ViewModel increment: six JVM tests cover validation/exact money,
      repository error propagation and ViewModel states.
- [x] Plain Room write/read, close/reopen, force-stop/relaunch preserve IDs/values;
      SQLite integrity check passes.
- [x] Multi-write failure rolls back; interrupted database creation retries
      consistently; unavailable/corrupt storage does not trigger an automatic reset.
- [x] Reproducible v1 fixture upgrades to v2 preserving fields and nullable note,
      passes Room validation and second reopen; migration failure preserves files.
- [x] Add/recreate/relaunch UI journey, light/dark/large text/TalkBack labels/system bars;
      runner rejects missing/offline serials/unexpected IDs, times out and returns
      failure on failed assertions, produces machine-readable sanitized results.
- [x] Permanent signing-key N to N+1 upgrade retains records and database access.
- [x] zipalign and apksigner pass; ELF alignment passes for any remaining packaged
      native libraries. SQLCipher-specific packaging assertions are removed.
- [x] Secrets/private records excluded; focused verified commits; docs/limitations
      and concrete Phase 2 handoff recorded.

Full navigation, financial schema, attachments, portable backup implementation,
recognition behavior and biometric lock remain deferred. Instrumentation is the
proof requirement; any ADB/debug harness alternative requires explicit acceptance
of coverage limits. Static native checks do not establish 16 KB runtime support.

## Limitations and Handoff

No remaining Phase 1 implementation blocker. Other Android versions, other device
sizes, 16 KB runtime, spoken TalkBack, Android Studio sync and actual OS backup/
device-transfer execution are unverified. Interrupted creation uses an injected
callback failure, not a physical power cut. Native libraries pass static checks;
the clean build packages Compose's prebuilt library without extra symbol stripping.

The signing key has not been backed up off-machine. A user-controlled destination
and successful recovery check must be recorded before real bookkeeping use.
This is separate from future encrypted exported database backups. Never regenerate
the release identity to resolve a missing local key.

Phase 2 has not started. Its next concrete action, when authorized, is to create
`02-manual-bookkeeping.md` around the actual modules and design/test the separate
financial schema and posting invariants first. Reuse the composition root,
storage initialization/failure handling, migration tests, explicit device runner,
signing identity and release upgrade checks. Keep `foundation-proof.db` disposable
and separate; do not treat samples as financial transactions. Then implement
setup/accounts/categories/manual entry/transfers and encrypted backup/restore.
Recognition fixtures/provider contracts remain Phase 3 work.
