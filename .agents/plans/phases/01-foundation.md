# Phase 1: Foundation and Build-System Proof

Status: bounded Toolchain proof verified; awaiting user's build-system decision.
Phase 1 is incomplete. No foundation implementation beyond the isolated proof is authorized before this gate.

## Scope and Gate

Implement the user-approved Phase 1 plan. Kochenite uses release application ID
`moe.frost.kochenite`, debug ID `moe.frost.kochenite.debug`, minimum API 26 and
compile/target API 36. Synthetic data only. Shared architecture and scope remain
in [the master plan](../PLAN.md).

Proof budget: four hours of active engineering, beginning 2026-09-21 15:09 UTC
(host clock; local date September 22). Stop earlier on decisive evidence. Use
documented configuration/public extensions; no binary patches or maintained edits
to generated Gradle projects. Present commands/results/custom code/uncertainties
for each capability. The user chooses Toolchain or Gradle before the remaining
milestones begin. No automatic fallback or implicitly accepted debug harness.

## Milestones and Implementation

1. Rediscover phone/SDK, scaffold pinned 0.12.2 proof; build/install Compose debug;
   JVM test; debug/release IDs and source/resource isolation; separate test APK
   with AndroidJUnitRunner and real Compose assertion; Room 2.8.4/KSP2 schema
   export; maintained SQLCipher encrypted device write/read; temporary-key signed,
   non-debuggable shrunk release; signature/native packaging verification.
2. After the decision, establish app, pure Kotlin domain, Android persistence and
   platform, and unimplemented recognition boundary. Composition root/constructor
   injection, ViewModel/StateFlow, background database work. Sample list/add action,
   loading/empty/error states, externalized strings, light/dark, accessible targets
   and system-bar insets.
3. Disposable `foundation-proof.db`: stable IDs, integer minor units, currency
   code/scale, UTC timestamps; v2 adds nullable note. Narrow observable/atomic
   sample repository and key provider; ready/temporarily unavailable/recovery
   required initialization. Random passphrase wrapped by Keystore AES-GCM,
   atomically saved before database creation, fresh nonces, no biometric gate.
   Unlock-aware access; preserve files on missing/invalid keys, authentication,
   corruption or migration failures. No destructive migration/replacement keys
   for existing databases. Backup/device-transfer exclusions; no new permissions.
4. Explicit-serial bounded test runner, sanitized results and repeatable builds.
   Durable password-protected key under ignored `.local/signing`, restricted
   permissions, public fingerprint, encrypted backup/recovery instructions.
   Actual off-machine backup needs a user-controlled destination before real use.

## Exit Criteria

- [ ] Build-system choice recorded and clean-output debug/release builds repeat.
- [ ] Debug and signed release run on the reference phone.
- [ ] JVM tests cover validation/exact money, repository errors, ViewModel states.
- [ ] Encrypted write/read, close/reopen, force-stop/relaunch preserve IDs/values;
      ordinary SQLite/wrong key rejected; encrypted integrity check passes.
- [ ] Multi-write failure rolls back; interrupted key/database creation retries
      consistently; unavailable/invalid keys do not reset data.
- [ ] Reproducible v1 fixture upgrades to v2 preserving fields and nullable note,
      passes Room validation and second reopen; migration failure preserves files.
- [ ] Add/recreate/relaunch UI journey, light/dark/large text/TalkBack/system bars;
      runner rejects missing/offline serials/unexpected IDs, times out and returns
      failure on failed assertions, produces machine-readable sanitized results.
- [ ] Permanent-key signed N to N+1 upgrade retains records and Keystore access.
- [ ] zipalign, ELF load alignment and apksigner checks pass.
- [ ] Secrets/private records excluded; focused verified commits; docs/limitations
      and concrete Phase 2 handoff recorded.

Full navigation, financial schema, attachments, portable backup implementation,
recognition behavior and biometric lock remain deferred. Instrumentation is the
proof requirement; any ADB/debug harness alternative requires explicit acceptance
of coverage limits. Static native checks do not establish 16 KB runtime support.

## Session Evidence and Next Action

- Initial master branch has no application code; user-owned untracked `.codex/`
  and `receipt-ai-demo/` preserved.
- `kotlin --version`: 0.12.2 (82a1532, 2026-09-15), default caches succeed.
- `android --no-metrics --version`: 1.0.16261425; SDK is
  `/home/amber/.local/share/android-sdk`. Initially API 35 only; API 36 revision 2
  was installed through approved access. Final builds use build tools 37.0.0.
- `adb devices -l`: one connected ONEPLUS_A6013; explicitly selected commands
  confirm API 35 and 4096-byte pages. Other OS/page-size coverage is unverified.
- `kotlin init --target-dir toolchain-proof`: Compose template generated.
- Plan write initially failed: `Failed to create parent directories for
  /home/amber/codex_workspace/kochenite-v2/.agents/plans/phases/01-foundation.md`;
  `.agents` is read-only in the sandbox. Approved access is required for updates.
- The user corrected the host environment by removing deprecated
  `ANDROID_SDK_HOME`; new commands retain `ANDROID_USER_HOME` and existing caches.
  The initial AndroidLocationsException is resolved without an agent workaround.
- Two JVM exact-money tests pass, including a second run after clean outputs.
  Compose debug and standalone test APKs built and installed on current user 0.
  Room 2.8.4/KSP2 2.3.11 generated schema v1 and compiled after DAO methods were
  made suspend. Subsequent device storage checks passed as recorded below.
- Native Android variant qualifiers are unsupported (`settings@debug` rejected).
  Proof uses thin debug/release app modules sharing a UI library. Temporary
  release ID is `moe.frost.kochenite.debug.releaseproof`; permanent release ID
  remains reserved for the durable key after the decision gate.
- Built-in Android tests are host JVM tests. Standalone AndroidJUnitRunner test
  APK launches, but its first test failed because it resolved Activity 1.7.0 while
  the app used 1.13.0. Pinned 1.13.0 in the test module; both tests then passed.
- Debug manifest-only edits were ignored by incremental packaging. A clean build
  applied explicit removal of legacy storage/phone permissions introduced by
  Toolchain's manifest stubs. Android library `R` resources/user manifests are
  unsupported in the inspected implementation; shared strings now use documented
  Compose resources. These are decision-matrix costs, not silently patched tools.
- SQLCipher 4.19.0 compiled but AAR packaging requires API 37. Selected 4.17.0
  (latest release before that requirement changed) with SQLite 2.6.2 for API 36.
  Final native checks passed for 4.17.0; earlier input-library checks on 4.19.0
  were not counted as evidence for the selected binary.
- Temporary 30-day proof key generated under ignored `.local/proof-signing`,
  directory mode 700, files mode 600. No permanent key exists yet.
- `scripts/run-proof-tests.mjs` requires an explicit serial, fixed proof packages,
  bounded commands and both test classes; rejects missing/nonexistent devices and
  instrumentation failures; writes only a sanitized JSON summary.
- The user unlocked the phone. Both real device tests passed: Compose UI adds a
  sample; Room/SQLCipher preserves an exact large integer, stable ID, currency and
  timestamp through close/reopen, returns no encrypted integrity errors, and lacks
  the plaintext SQLite header. Sanitized runner JSON reports 2 completed tests.
- Shared UI uses documented Compose resource generation successfully. Debug and
  signed, non-debuggable release APKs run on the OnePlus; release add/force-stop/
  relaunch journey passes with retained sample count and amount. R8 mapping exists;
  no custom keep rules were necessary for this probe.
- Both APKs pass apksigner, zipalign with 16 KB mode, and checks of all 8 packaged
  native libraries (SQLCipher 4.17.0 plus Compose graphics on 4 ABIs). Debug code
  and resource markers are present in debug and absent in release. No platform
  runtime permission declarations remain in either app APK.
- Light-mode screenshot and system-bar clearance inspected. Dark/large-text/
  TalkBack operation and all other OS/page-size runtime coverage remain unverified.
  The earlier locked black capture was rejected as evidence.
- Sequential clean-output debug/test rebuild passed. A concurrent debug/release
  attempt raced on shared KSP outputs and failed; run CLI builds sequentially.
  Final clean-output release build, both JVM tests, both device instrumentation
  tests, APK checks, and release add/force-stop/relaunch journey all passed.
  One release journey failed on an empty Android CLI layout response. The runner
  now retries empty responses within its existing bounded loop; the rerun passed.
- Proof verification stopped at 2026-09-21 16:35 UTC, 86 minutes after its start,
  within the four-hour active-engineering limit. Proof files and documentation
  form the focused milestone commit; keys, APKs and device evidence stay ignored.
- Full matrix, commands, temporary-key fingerprint and tradeoffs are in the
  [proof report](../../../docs/toolchain-proof.md). Recommendation is Gradle for
  remaining work due to cumulative Android module, variant, instrumentation and
  incremental packaging costs. Toolchain remains technically viable with the
  documented constraints. No build-system choice or migration has been made.
- Next concrete action: user's explicit build-system choice, then milestone 2
  and remaining Phase 1 storage/key/recovery/migration/signing work. Do not start
  Phase 2; no durable key, financial schema or recognition behavior exists yet.
