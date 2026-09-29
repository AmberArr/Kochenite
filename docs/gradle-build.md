# Gradle Build and Verification

Kochenite uses Gradle following the [Toolchain proof](toolchain-proof.md).
The active app implements local financial bookkeeping and uses plain Room/SQLite
in private credential-protected storage; there is no SQLCipher or database-key
layer. Android's sandbox and device encryption protect files while that
protection holds. Extracted database files are ordinary readable SQLite. Use
synthetic records until the Phase 2 release and recovery gates are complete.
The retired Phase 1 `foundation-proof.db` is no longer opened or migrated; any
existing copy is left untouched.

## Structure and Versions

- `app`: Compose UI, ViewModel, explicit composition root and Android variants.
- `domain`: Android-independent Kotlin models and repository interfaces.
- `persistence`: financial Room entities/DAOs and migrations, unlock-aware
  initialization and non-destructive corruption handling for `bookkeeping.db`.
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

`verify` combines Spotless, app/persistence Android Lint, domain JVM tests,
recognition-boundary checks and host script tests. It needs the normal SDK/JDK
and Node.js, but no connected phone or release signing key. It does not replace
the financial instrumentation below.

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

### Crash Diagnostics

Release builds embed `kocheniteSentryDsn` from `gradle.properties` and report
through `sentry-android-core` (no native handler or replay). The DSN only
allows event submission. `app/src/main/kotlin/Diagnostics.kt` owns the privacy
options and scrubbing. Debug builds report only when built with
`-PkocheniteSentryDebug=true`; rebuild without it before running device tests.
The app requests `android.permission.INTERNET` for this purpose only.

### APKs and Devices

Use the workspace root, installed SDK/JDK and default caches. Restore release
signing using [these instructions](release-signing.md) before release builds.
No emulator is used. Run build invocations sequentially, including `clean`.

```sh
./gradlew :domain:test :app:assembleDebug :app:assembleDebugAndroidTest :persistence:assembleDebugAndroidTest
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
node scripts/run-financial-tests.mjs "$ANDROID_SERIAL" --ui
adb -s "$ANDROID_SERIAL" install --user 0 -r app/build/outputs/apk/release/app-release.apk
node scripts/check-apk.mjs app/build/outputs/apk/debug/app-debug.apk "$ANDROID_HOME" debug
node scripts/check-apk.mjs app/build/outputs/apk/release/app-release.apk "$ANDROID_HOME" release
```

Unlock the phone for UI tests. The financial instrumentation runner verifies the
storage test package and instrumentation targets, uses bounded ADB calls, checks
test counts and assertion statuses, and exits nonzero on failures. It runs seven
financial-storage tests, fourteen ledger regressions and six book-setup tests.
With `--ui`, it also runs seven financial UI checks and four process checks for
unsaved editor loss and explicitly saved incomplete survival. Storage tests create and delete random synthetic databases; UI tests use
the separate debug app. The runner never clears app data.

Financial migration tests use checked-in Room schemas and `MigrationTestHelper`
to verify exact-value preservation, rollback and retry. The v6→v7 migration
retires legacy ordinary editor rows while preserving explicitly saved incomplete
records. Corruption tests prove byte retention across repeated failed opens; the
SQLite factory replaces the default deleting corruption callback and disables
data-loss recovery.

When updating the old Toolchain test APK only, a one-time `install -r -d` may be
needed for `moe.frost.kochenite.debug.test` (AGP test version 0 replaces version 1).
Never downgrade or uninstall the retained release app to solve installation errors.
Historical encrypted `toolchain-probe.db` files and temporary release package
`moe.frost.kochenite.debug.releaseproof` are not opened, reset or converted.

## Release Artifacts and Evidence

Default release version is 2. Choose a code greater than the installed release
for the next upgrade; e.g. version 3 after version 2:

```sh
./gradlew :app:assembleRelease -PreleaseVersionCode=3
node scripts/check-apk.mjs app/build/outputs/apk/release/app-release.apk "$ANDROID_HOME" release
```

The APK checker validates artifact identity, signature, alignment and debug
isolation. It does not exercise persisted financial records in a release upgrade;
that remains part of the Phase 2 release-validation gate. Do not run `clean` or
rebuild while a runner is consuming an APK.

Current financial verification evidence stays in ignored
`.local/financial-evidence` as sanitized JSON summaries. No device logcat,
credentials or financial records are saved. Test errors report only the stage
and failure category. Historical Phase 1 evidence is linked from its archived
[worklog](worklogs/01-foundation.md).

## Workspace Cleanup

Run `./gradlew clean` when no build or APK runner is active to remove generated
project outputs. APKs and test reports under `build/` are regenerated by the
commands above. Keep the default tool caches for subsequent builds.

Keep `.local/signing`: its key and credentials are required for future release
updates. Do not delete `.local` wholesale; it also contains sanitized
verification summaries and historical evidence. The APK checker automatically
removes its temporary native-library extraction.
Retired Toolchain module directories are no longer needed in the workspace;
their sources remain in Git history. User skills, `.codex` configuration and the
supplied recognition demo are separate from build output and must be preserved.

## Historical Phase 1 Coverage

The earlier sample app, database and test harness passed the checks recorded in
the [Phase 1 worklog](worklogs/01-foundation.md). Those checks remain historical
evidence for the build/storage approach; they do not verify the active financial
UI or its release-upgrade behavior. The current transaction-editor redesign has
not been build- or device-verified yet. Off-machine signing backup remains a
prerequisite for real use.

Future recognition development will use
`adb -s "$ANDROID_SERIAL" reverse tcp:8787 tcp:8787`; enable localhost cleartext
only in debug when that service is added. No recognition server or network
permission is introduced in this phase. Future iOS work can use Gradle/KMP with
the independent domain; iOS targets remain unimplemented.
