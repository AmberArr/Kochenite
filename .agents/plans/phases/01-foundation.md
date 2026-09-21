# Phase 1: Foundation and Build-System Proof

Status: Toolchain proof, Gradle migration, domain/ViewModel, plain Room integration
and eleven physical-device assertions verified. Durable signing material exists.
Final clean-build, release-upgrade and documentation checks are in progress.

## Scope and Gate

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

Historical proof budget: four hours of active engineering, beginning 2026-09-21 15:09 UTC
(host clock; local date September 22). Stop earlier on decisive evidence. Use
documented configuration/public extensions; no binary patches or maintained edits
to generated Gradle projects. Present commands/results/custom code/uncertainties
for each capability. The user chooses Toolchain or Gradle before the remaining
milestones begin. No automatic fallback or implicitly accepted debug harness.

## Milestones and Implementation

1. Completed historical proof: rediscover phone/SDK, scaffold pinned 0.12.2 proof; build/install Compose debug;
   JVM test; debug/release IDs and source/resource isolation; separate test APK
   with AndroidJUnitRunner and real Compose assertion; Room 2.8.4/KSP2 schema
   export; the then-planned encrypted device write/read; temporary-key signed,
   non-debuggable shrunk release; signature/native packaging verification. The
   user then selected Gradle; its migration and repeat builds are verified.
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
   Backup/device-transfer exclusions; no new permissions. Remove the unfinished
   database-key layer, SQLCipher helper adapter, and encryption-specific tests.
4. Explicit-serial bounded test runner, sanitized results and repeatable builds.
   Durable password-protected key under ignored `.local/signing`, restricted
   permissions, public fingerprint, encrypted backup/recovery instructions.
   Actual off-machine backup needs a user-controlled destination before real use.

## Exit Criteria

- [x] Build-system choice recorded and clean-output proof debug/release builds repeat.
- [ ] Debug and signed release run on the reference phone.
- [x] Domain/ViewModel increment: six JVM tests cover validation/exact money,
      repository error propagation and ViewModel states. Rerun after integration.
- [x] Plain Room write/read, close/reopen, force-stop/relaunch preserve IDs/values;
      SQLite integrity check passes.
- [x] Multi-write failure rolls back; interrupted database creation retries
      consistently; unavailable/corrupt storage does not trigger an automatic reset.
- [x] Reproducible v1 fixture upgrades to v2 preserving fields and nullable note,
      passes Room validation and second reopen; migration failure preserves files.
- [ ] Add/recreate/relaunch UI journey, light/dark/large text/TalkBack/system bars;
      runner rejects missing/offline serials/unexpected IDs, times out and returns
      failure on failed assertions, produces machine-readable sanitized results.
- [ ] Permanent signing-key N to N+1 upgrade retains records and database access.
- [ ] zipalign and apksigner pass; ELF alignment passes for any remaining packaged
      native libraries. SQLCipher-specific packaging assertions are removed.
- [ ] Secrets/private records excluded; focused verified commits; docs/limitations
      and concrete Phase 2 handoff recorded.

Full navigation, financial schema, attachments, portable backup implementation,
recognition behavior and biometric lock remain deferred. Instrumentation is the
proof requirement; any ADB/debug harness alternative requires explicit acceptance
of coverage limits. Static native checks do not establish 16 KB runtime support.

## Session Evidence and Next Action

The encryption-related results below describe earlier proof builds. They are
retained as history and do not impose encryption requirements on the foundation.

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
  documented constraints. The user subsequently selected Gradle.
- Decision: on September 22, 2026 (local date), the user explicitly approved
  migration to Gradle after discussing future iOS portability. Keep the domain
  independent of Android; no iOS targets or Multiplatform migration in this phase.
- Migration increment: pin Gradle 9.5.0/AGP 9.3.1/Kotlin 2.4.10/KSP 2.3.11,
  use standard source sets, one app with debug/release variants and androidTest,
  retain the temporary proof release identity until durable signing is configured.
  Verify JVM tests, APKs, instrumentation and release journey before expanding.
- Gradle migration verified: pinned wrapper checksum and JAR match published
  values; `help`, `build --dry-run`, two JVM tests, debug, standard androidTest and
  signed R8/resource-shrunk release builds pass. A second build after `clean` also
  passes. Room/KSP exports the identical v1 schema. Android library manifests and
  resources now use standard AGP support; no permission-removal workaround needed.
- Both Gradle-built instrumentation tests pass on the selected OnePlus API 35/
  4 KB device. Release add/force-stop/relaunch passes. Both APKs pass signature,
  SDK/package/debug isolation and native alignment checks. Existing proof packages
  were updated with `install -r`; only the disposable test APK needed `-d` because
  AGP generates version code 0 versus the prior standalone test APK's 1.
- Gradle initially failed on its default native lock under
  `/home/amber/.local/share/gradle` (read-only sandbox). Approved execution works;
  no cache or environment redirection. `:app:lintDebug` passes with version/API,
  unused marker, missing icon and backup-rule warnings documented in
  [the Gradle report](../../../docs/gradle-build.md). IDE sync remains untested.
- Foundation increment: pure domain and deferred recognition boundary added.
  ViewModel has observable loading/ready/unavailable/recovery states, guarded
  insertion, retry and repository error propagation. Two domain and four ViewModel
  JVM tests pass with fakes. Persistence integration is being implemented next.
- The subsequent uncommitted integration adds the foundation screen/composition
  root, backup exclusion XML, and a `persistence` module. Its SQLCipher/Keystore
  implementation and eleven storage tests are not verified and are superseded
  by the user's plain Room decision. The old app instrumentation tests were removed
  during this work; replacement app UI/restart tests remain to be implemented.
- Latest build failed in `app/build.gradle.kts`: `Task with name 'preReleaseBuild'
  not found in project ':app'`. The signing guard is registered before AGP creates
  that task. Earlier persistence attempts also exposed an AGP source-set API cast
  error; the explicit modern LibraryExtension adjustment is not yet verified.
- User decision: remove SQLCipher/database-key management from the plan and use
  plain Room/SQLite. This update changes plans only; implementation files remain
  untouched pending the next implementation step. No durable release signing key,
  financial schema or recognition behavior exists yet.

## Remaining Work

Current implementation evidence supersedes the older failure notes above:

- Removed active SQLCipher, passphrase/Keystore code and retired proof modules.
  Historical databases and the temporary release package were left untouched.
- Fixed the lazy release-task hook and AGP library source-set configuration.
  The integrated debug, test APKs and signed/shrunk release now build.
- Room schema v2 is exported. Seven storage tests pass: exact integer/ID roundtrip,
  integrity and transaction rollback, v1 migration with two reopens, failed migration
  rollback, corruption file retention, locked storage, temporary failure/retry, and
  interrupted creation/retry. Tests use unique disposable databases.
- Four app assertions pass: add/recreate, exact persisted checkpoint after process
  force-stop, light/dark/2x text with accessible button semantics, and UI error states.
  All eleven tests ran through separately packaged AndroidJUnitRunner APKs on the
  explicitly selected OnePlus API 35/4 KB device. Screenshots contain Kochenite only.
- Six JVM tests pass. Lint: no errors, eleven app and six persistence warnings,
  concerning pinned versions/API and the deliberate unused debug isolation marker.
- Permanent RSA-3072 signing identity generated in ignored `.local/signing` with
  directory mode 700 and files 600. Public certificate is recorded in
  `docs/release-certificate.sha256`. Off-machine backup is not yet completed.
- Signed release version 1 runs; add/force-stop/relaunch passes. Both APKs pass
  signature, ID/SDK/debug isolation, ZIP 16 KB and four Compose native ELF checks.
  No SQLCipher native libraries remain. Actual 16 KB execution is unverified.
- Final palette correction, modern Compose test-rule APIs, clean build and signed
  upgrade verification are in progress. Full spoken TalkBack operation remains
  unverified; automated semantic labels/actions have been checked.

The original remaining-work checklist below is retained until final verification;
items 1-3 are implemented and verified, and items 4-6 are substantially complete.

1. Restore a working integrated build: fix release-signing task registration,
   verify the library source-set configuration, remove SQLCipher/key-management
   code and dependencies, and retire active proof modules. Preserve historical
   encrypted proof databases; do not reinterpret them as plaintext or silently reset them.
2. Finish the foundation screen/composition root with the existing domain and
   ViewModel, plain Room repository and explicit storage states. Verify background
   database access, resource strings, icon, backup/device-transfer exclusions and
   absence of camera/notification/storage permissions.
3. Verify storage: exact values and stable IDs through reopen and process restart,
   integrity, multi-write rollback, interrupted initialization, unavailable/corrupt
   storage, v1-to-v2 nullable-note migration, second reopen and failed-migration
   data retention. Keep reusable tests; remove key/wrong-passphrase/cipher tests.
4. Restore and adapt the explicit-serial instrumentation/device runner to the
   foundation packages and tests. Verify add/recreate/force-stop/relaunch, bounded
   waits, rejected missing/offline devices and unexpected IDs, failing exit codes,
   and sanitized results. Check light/dark, large text, TalkBack labels and insets.
5. Create the durable password-protected release signing key under ignored
   `.local/signing`, restrict permissions, record its public fingerprint, and test
   signed N-to-N+1 installation without clearing records. Document encrypted key
   backup/recovery; actual off-machine backup needs a user-controlled destination
   before real bookkeeping use. Never silently regenerate a missing signing key.
6. Repeat clean debug/release builds and relevant JVM/device tests; verify R8,
   signatures, APK alignment and remaining native libraries. Record lint results
   and untested OS/page-size coverage, including unverified 16 KB runtime behavior.
7. Update current build/test/release documentation to replace proof-only commands,
   make focused verified commits, close supported exit criteria, and document the
   concrete Phase 2 handoff. Do not begin Phase 2 before Phase 1 is verified.

Next concrete action: finish clean-output verification, signed version 1-to-2
upgrade, screenshot review and current workflow documentation. Then record the
remaining coverage limits and Phase 2 handoff. Do not begin Phase 2 yet.
