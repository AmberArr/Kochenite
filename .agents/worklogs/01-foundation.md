# Phase 1 Worklog

Implementation history and verification evidence for the
[Phase 1 plan](../plans/phases/01-foundation.md). Product scope and current
decisions live in the [master plan](../plans/PLAN.md); operational commands live
in the [build guide](../../docs/gradle-build.md).

Entries below were moved from the plans on September 22, 2026. Historical notes
describe the state at the time, including superseded failures, SQLCipher probes
and temporary signing. They are not current requirements or unresolved blockers.
Use the final foundation evidence and subsequent entries for completed checks;
use the phase plan for current limitations and next steps.

## Archived Environment and Proof Setup

Workspace: `/home/amber/codex_workspace/kochenite-v2`.

The project started without application code. The user initialized Git and configured `init.defaultBranch` to `master`. Existing `.agents/` skills are user-provided resources; do not commit or modify them incidentally.

This machine does not support KVM. Do not create or launch an Android emulator. Build on the host and deploy, inspect, and test on a remote physical phone using ADB.

At planning time, wireless ADB connected to a OnePlus 6T (`ONEPLUS_A6013`) running Android 15 / API 35. Its observed serial was `adb-a2480128-JKi84V._adb-tls-connect._tcp`; rediscover the current serial with `adb devices -l` in each session and select it explicitly. Do not assume the serial remains stable. Never clear or uninstall the user's Bluecoins app or change its records during reference inspection.

Use the checked-in Gradle 9.5.0 wrapper and Kotlin DSL build from the workspace root with default tool caches. AGP 9.3.1, Kotlin 2.4.10, KSP2 2.3.11 and JDK 21 are verified together. If permissions block a tool/cache path, report the exact error and request required access; do not redirect caches or repurpose environment variables. Keep generated outputs and caches ignored. The prior Toolchain build is preserved in commit `368ce38`.

The user selected Spotless + ktlint for Kotlin/Gradle formatting. Pin Spotless 8.10.2 and ktlint 1.8.0; use official ktlint defaults with the documented Compose naming exception. `./gradlew verify` combines formatting, Android Lint, JVM and host script tests; device checks remain explicit. A versioned, check-only pre-commit hook checks staged whitespace and formatting, refusing ambiguous partial staging without changing files or the index. Exclude generated output, secrets, local skills and supplied demo code from formatting.

Toolchain 0.12.2 built and installed the Compose, Room/KSP2/SQLCipher and signed/shrunk release probes. The [capability matrix](../../docs/toolchain-proof.md) records commands, integration costs and limits. The user explicitly approved migration to Gradle on September 22, 2026 (local date). Use the pinned Gradle wrapper, standard Android variants and instrumentation for remaining Phase 1 work.

Android CLI 1.0.16261425 and ADB work. Always pass `--no-metrics`. API 36 revision 2 and command-line tools 22.0 are installed; builds use tools 37.0.0 and pinned JDK 21. The user removed conflicting `ANDROID_SDK_HOME` and retained `ANDROID_USER_HOME`; no cache redirection was applied.

Historical proof budget: four hours of active engineering, beginning 2026-09-21 15:09 UTC
(host clock; local date September 22). Stop earlier on decisive evidence. Use
documented configuration/public extensions; no binary patches or maintained edits
to generated Gradle projects. Present commands/results/custom code/uncertainties
for each capability. The user chooses Toolchain or Gradle before the remaining
milestones begin. No automatic fallback or implicitly accepted debug harness.

## Proof and Implementation History

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
  [proof report](../../docs/toolchain-proof.md). Recommendation is Gradle for
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
  [the Gradle report](../../docs/gradle-build.md). IDE sync remains untested.
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

## Final Foundation Evidence

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
- Final neutral/teal palette and Compose v2 test rules verified. Light/dark/2x
  text screenshots reviewed without clipped labels or overlapping controls.
  Release screenshot verifies status/navigation bar clearance. Spoken TalkBack
  operation remains unverified; semantic labels/actions have been checked.
- `./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug
  :app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest
  :app:assembleRelease :app:lintDebug :persistence:lintDebug -PreleaseVersionCode=2`
  passed. Reinstalled the clean-output debug/test APKs with `install -r` and reran
  `node scripts/run-device-tests.mjs SERIAL`: eleven tests passed again.
- `node --test scripts/instrumentation-result.test.mjs`: four tests pass for
  successful, failed, skipped/incomplete and wrong-count reports. Runner rejects
  missing serial and nonexistent device with exit 1. APK check rejects a debug
  package presented as release. Instrumentation targets are checked exactly and
  commands have bounded timeouts. Summaries contain no assertion values or logs.
- `node scripts/run-release-journey.mjs SERIAL --upgrade
  app/build/outputs/apk/release/app-release.apk SDK`: version 1-to-2 upgrade passed
  using the same permanent key, retaining count and visible amount/date values
  without clearing storage, followed by another successful restart. Exact IDs and
  all fields across process restart are covered by the debug instrumentation.
- Both clean-output APKs pass `scripts/check-apk.mjs`. Release version defaults
  to 2; a subsequent default `:app:assembleRelease` also passed. Manifest inspection
  confirms backup/transfer rules, cleartext disabled and no platform permissions;
  only AndroidX's own signature-protected receiver permission is requested.
- The key-generation script refuses replacement of the existing identity. Private
  files/evidence are ignored; only the public SHA-256 certificate is committed:
  `9cf15b4df2e8b4821e9b3c1d1d494e14e53398c694e2c3aee422666222ab304c`.
- Foundation/persistence milestone committed as `bc5ea8d`; release/test workflow
  and final evidence are a separate focused commit. User `.codex/` and demo files
  remain untouched and unstaged. Branch remains `master`.
- Current commands, evidence locations, dependencies and future ADB reverse are
  in [the build guide](../../docs/gradle-build.md); encrypted signing backup
  and recovery procedure is in [the signing guide](../../docs/release-signing.md).

## Post-Foundation Tooling

The user selected Spotless + ktlint after Phase 1, before starting Phase 2.
Pinned Spotless 8.10.2 and ktlint 1.8.0 through the existing wrapper; no global
ktlint installation is needed. Root configuration targets only project Kotlin
sources and Gradle Kotlin DSL. `.editorconfig` uses official ktlint rules with
the documented naming exception for `@Composable` functions. Wildcard imports
were expanded; existing sources are normalized in a separate style commit.

Added `verify` for Spotless, app/persistence Android Lint, six JVM tests,
recognition-boundary checks and host script tests. The versioned pre-commit hook
is check-only: staged whitespace for every commit, full formatting check for
Kotlin/build/config changes, and refusal of unstaged changes in those tracked
files. No auto-staging, rewriting or stashing. Six temporary-repository hook tests
verify skipping docs-only commits, triggering for module sources, preserving the
index and unrelated edits, and rejecting partial staging/whitespace/formatter
failures. These pass, alongside the four existing runner-parser tests.

Formatter setup exposed stale generated lint diagnostics after an EditorConfig
change; restarting the idle daemon and clearing root formatter outputs resolved
them. One throw-only callback needed its equivalent explicit Unit expression to
normalize cleanly. `spotlessApply`, `spotlessCheck`, full `verify` and compilation
of both instrumentation source sets pass. A second formatter pass leaves the diff
unchanged. Lint remains at zero errors (eleven app and six persistence warnings).
The tooling setup and source normalization are separate commits. The local hook
is enabled via `core.hooksPath=.githooks` and passed against the staged formatting
baseline. Device tests were not rerun
for these formatting/import-only changes. Phase 2 has not started.

## Workspace Cleanup

Removed generated root/module builds, retired Toolchain module directories,
disposable variant probes, old proof build logs and extracted native libraries.
Workspace usage fell from approximately 265 MB to 19 MB. Default tool caches
remain in place. Signing identities, sanitized result summaries, screenshots,
user skills, `.codex` and the supplied recognition demo are preserved; file hashes
confirmed preservation during cleanup.

The APK checker now removes its extraction directory in a `finally` block.
Debug and signed release APK checks passed before removing build outputs;
`git diff --check` passed. No application code changed or device tests reran.
Rebuild APKs with the documented commands when next needed. Phase 2 remains
unstarted; its next action and the off-machine signing-backup requirement above
are unchanged.

## September 22, 2026: Separate Plans and Worklogs

Moved session history, command results, resolved blockers, tooling verification
and cleanup records out of the plans into this worklog. Plans retain scope,
accepted decisions, deliverables, exit criteria, current limitations and next
actions. Updated session instructions and documentation links to use this split.
Verification passed: 26 relative links/anchors resolve, moved historical evidence
is preserved, all ten completed exit criteria remain and `git diff --check` is
clean. No application build or device tests ran for this documentation change.
Phase 2 planning remains the next action when authorized; the signing-backup
prerequisite is unchanged.
