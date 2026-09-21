# Foundation Build and Verification

Kochenite uses Gradle following the [Toolchain proof](toolchain-proof.md).
The app accepts synthetic samples only. Plain Room/SQLite lives in private
credential-protected storage; there is no SQLCipher or database-key layer.
Android's sandbox and device encryption protect the files while that protection
holds. Extracted database files are ordinary readable SQLite.

## Structure and Versions

- `app`: Compose UI, ViewModel, explicit composition root and Android variants.
- `domain`: Android-independent Kotlin models and repository interfaces.
- `persistence`: Room entities/DAO, migration, unlock-aware initialization and
  non-destructive corruption handling. `foundation-proof.db` is a disposable
  sample schema, separate from the future financial database.
- `recognition-contract`: unimplemented boundary; design and fixtures wait for Phase 3.

Pinned: Gradle 9.5.0, AGP 9.3.1 (built-in Android Kotlin), Kotlin/Compose compiler
2.4.10, KSP2 2.3.11, JDK 21, Room 2.8.4, SQLite 2.6.2, Coroutines 1.10.2,
Activity Compose 1.13.0, Compose UI/Foundation 1.11.2, Material3 1.5.0-alpha17,
Lifecycle 2.9.4 and AndroidJUnitRunner 1.7.0. Minimum API 26, compile/target 36.
App Build Tools 37.0.0; libraries use AGP's default 36.0.0. Dependencies are pinned
in module build files. The wrapper distribution checksum is checked in; wrapper
JAR SHA-256 is `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.

## Build and Install

### Formatting and Local Checks

Spotless 8.10.2 runs ktlint 1.8.0 through Gradle; a global ktlint installation is
not needed. `.editorconfig` selects ktlint's official defaults, with the documented
function-naming exception for `@Composable`. Other standard rules remain enabled.
Kotlin sources in the four project modules and root/module Gradle Kotlin DSL files
are included. Generated build outputs, Room schemas, `.local`, user skills,
`.codex` and `receipt-ai-demo` are outside the formatter's targets.

```sh
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew verify
```

`verify` combines Spotless, app/persistence Android Lint, domain/app JVM tests,
recognition-boundary checks and host script tests. It needs the normal SDK/JDK
and Node.js, but no connected phone or release signing key. It does not replace
the explicit instrumentation and release journeys below.

Enable the checked-in commit hook once per checkout, after checking for an existing
`core.hooksPath` or custom `.git/hooks/pre-commit` that should be preserved:

```sh
git config --local core.hooksPath .githooks
```

The hook checks staged whitespace for every commit. Kotlin/build/config changes
also run a full `spotlessCheck`; docs-only changes skip Gradle. Since Spotless
reads the worktree, the hook rejects unstaged changes to tracked Kotlin/build/
config files when formatting checks are needed. Resolve those changes deliberately
before committing. It never autoformats, stages files, stashes changes or hides
partial-staging differences. Unrelated unstaged documentation is allowed. Checks
include all formatter targets, so an untracked Kotlin source within a module can
also produce a formatting failure. Hooks are local guardrails, not a replacement
for running `verify` in CI when CI is introduced.

### APKs and Devices

Use the workspace root, installed SDK/JDK and default caches. Restore release
signing using [these instructions](release-signing.md) before release builds.
No emulator is used. Run build invocations sequentially, including `clean`.

```sh
./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest
./gradlew :app:assembleRelease :app:lintDebug :persistence:lintDebug
node --test scripts/instrumentation-result.test.mjs
adb devices -l
```

Set `ANDROID_SERIAL` explicitly to the discovered OnePlus serial, and confirm the
current user with `adb -s "$ANDROID_SERIAL" shell am get-current-user`. The
reference device uses user 0; use the confirmed user on each install.

```sh
adb -s "$ANDROID_SERIAL" install --user 0 -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install --user 0 -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$ANDROID_SERIAL" install --user 0 -r persistence/build/outputs/apk/androidTest/debug/persistence-debug-androidTest.apk
node scripts/run-device-tests.mjs "$ANDROID_SERIAL"
adb -s "$ANDROID_SERIAL" install --user 0 -r app/build/outputs/apk/release/app-release.apk
node scripts/run-release-journey.mjs "$ANDROID_SERIAL"
node scripts/check-apk.mjs app/build/outputs/apk/debug/app-debug.apk "$ANDROID_HOME" debug
node scripts/check-apk.mjs app/build/outputs/apk/release/app-release.apk "$ANDROID_HOME" release
```

Unlock the phone for UI tests. The instrumentation runner checks the exact app,
test packages and instrumentation targets, uses bounded ADB calls, checks test
counts and assertion statuses, and exits nonzero on failures. It first runs seven
storage tests in the isolated library test package, then adds/recreates/checkpoints
debug records, force-stops the debug app, verifies every field in a fresh process,
and runs two presentation tests. It never clears app data. Test-only checkpoint
and screenshot code exists exclusively in `androidTest`; the test Activity is a
debug-only dependency. Release contains no testing controls.

MigrationTestHelper creates the v1 fixture from the checked-in Room schema, seeds
known exact values, validates migration to v2, then opens it twice. A separate
test interrupts the migration after ALTER and verifies rollback and retry. Room
callbacks interrupt initial creation inside its transaction; this models failure
at that boundary, not physical power loss. Corruption tests prove byte retention
across repeated failed opens; the SQLite factory replaces the default deleting
corruption callback and disables data-loss recovery.

When updating the old Toolchain test APK only, a one-time `install -r -d` may be
needed for `moe.frost.kochenite.debug.test` (AGP test version 0 replaces version 1).
Never downgrade or uninstall the retained release app to solve installation errors.
Historical encrypted `toolchain-probe.db` files and temporary release package
`moe.frost.kochenite.debug.releaseproof` are not opened, reset or converted.

## Release Upgrades and Evidence

Default release version is 2. Choose a code greater than the installed release
for the next upgrade; e.g. version 3 after version 2:

```sh
./gradlew :app:assembleRelease -PreleaseVersionCode=3
node scripts/run-release-journey.mjs "$ANDROID_SERIAL" --upgrade app/build/outputs/apk/release/app-release.apk "$ANDROID_HOME"
```

This adds a sample, restarts, validates the new APK's ID/signature/alignment and
higher version, installs with `-r`, checks the retained count and visible sample
amounts/dates, then restarts again. Exact IDs/all fields across process restart
are checked by debug instrumentation; release verification uses the public UI.
Do not run `clean` or rebuild while a runner is consuming an APK.

Evidence stays in ignored `.local/foundation-evidence`: sanitized JSON summaries,
app-only light/dark/2x text screenshots and the foreground release screenshot.
No device logcat, credentials or financial records are saved. Test errors report
only the stage and failure category; inspect individual synthetic test assertions
locally when diagnosing a failure. Never capture other apps.

## Workspace Cleanup

Run `./gradlew clean` when no build or APK runner is active to remove generated
project outputs. APKs and test reports under `build/` are regenerated by the
commands above. Keep the default tool caches for subsequent builds.

Keep `.local/signing`: its key and credentials are required for future release
updates. Do not delete `.local` wholesale; it also contains retained screenshots,
sanitized verification summaries and the historical proof signing identity.
The APK checker automatically removes its temporary native-library extraction.
Retired Toolchain module directories are no longer needed in the workspace;
their sources remain in Git history. User skills, `.codex` configuration and the
supplied recognition demo are separate from build output and must be preserved.

## Verified Coverage

- Clean project outputs rebuild debug, both instrumentation APKs and signed,
  non-debuggable R8/resource-shrunk release. Six JVM and eleven device tests pass;
  four host runner-parser tests pass. Repeatability is successful builds/behavior,
  not byte-identical APKs. No custom R8 keep rules are needed.
- OnePlus 6T, Android 15/API 35, 4096-byte pages: write/read/reopen, integrity,
  rollback, migrations and failure retention, add/recreate/force-stop/relaunch,
  and same-key release version 1-to-2 upgrade pass.
- Light/dark/2x text screenshots reviewed, labelled clickable controls with 48dp
  targets asserted, release status/navigation bar clearance inspected. Spoken
  TalkBack interaction was not exercised; semantic labels/actions were checked.
- Both APKs pass SDK/identity/debug isolation/signature, zipalign 16 KB mode and
  ELF LOAD alignment on all four Compose graphics native libraries. SQLCipher
  is absent. These static checks do not establish 16 KB runtime support.
- Lint passes: 0 errors; 11 app/6 persistence warnings for intentionally pinned
  API/dependency versions and the debug isolation marker. Clean builds report
  that Compose's native library is packaged without additional symbol stripping.
- Backup and device-transfer exclusions are declared; no camera, storage,
  notification or other platform permissions are requested. AndroidX adds its
  own signature-protected dynamic-receiver permission. Real OS restore/transfer
  execution, other OS/device sizes, 16 KB runtime and Android Studio sync remain
  unverified. Off-machine signing backup remains a prerequisite for real use.

Future recognition development will use
`adb -s "$ANDROID_SERIAL" reverse tcp:8787 tcp:8787`; enable localhost cleartext
only in debug when that service is added. No recognition server or network
permission is introduced in this phase. Future iOS work can use Gradle/KMP with
the independent domain; iOS targets remain unimplemented.
