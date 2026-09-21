# Kotlin Toolchain 0.12.2 Proof

Status: bounded proof reached the build-system decision gate. Phase 1 is incomplete;
the user has not chosen a build system. The `proof-*`/`toolchain-proof` modules are
disposable experiments with a public test database key, not a safe place for
financial records. Work began 2026-09-21 15:09 UTC; final verification completed
at 16:35 UTC (86 minutes elapsed, within the four-hour active-engineering budget).
No binaries or generated Gradle
projects were patched and no custom packaging framework was introduced.

## Recommendation and Tradeoffs

Recommend **Gradle** for the remaining foundation and later Android features.
Toolchain can build and run this proof through public interfaces, including real
instrumentation, but the combined testing, module and incremental-build constraints
are a recurring maintenance cost for this app:

- `settings@debug` is rejected. Distinct identities require separate thin app
  modules with shared library code; manifests/settings must be kept consistent.
- There is no built-in device-test APK pipeline. The standalone test APK experiment
  works, but packages its own dependency graph. Activity resolved to 1.7.0 there
  versus 1.13.0 in the app and caused a real `NoSuchMethodError` until aligned.
  Future dependency upgrades need explicit cross-APK alignment. Storage tests use
  the same source module packaged into the test APK; release ADB checks separately
  establish that the shrunk app's own database code executes.
- Android library modules do not generate `R` resources or merge user manifests;
  the pinned `AndroidAarTask.kt` has TODOs for both. Shared strings work through
  maintained Compose resources. Future services/providers must be declared in app
  manifests. This affects the intended platform/persistence module boundary.
- Debug manifest-only edits are absent from packaging task inputs. An incremental
  build reported success while retaining old permissions. `./kotlin clean` followed
  by build applied the changes. Clean after debug manifest changes until fixed.
- Generated library manifests without SDK declarations caused three unwanted
  legacy permissions. Standard `tools:node="remove"` rules remove them, and APK
  inspection verifies their absence. Preserve the artifact checks.

Retaining Toolchain is possible if the user accepts these constraints, thin app
modules, manually maintained instrumentation dependencies and clean-build checks.
This proof does **not** require substituting an in-app test message or ADB harness
for instrumentation: AndroidJUnitRunner and the Compose assertion actually passed.
No fallback is selected automatically. The user chooses before further foundation
implementation, durable key creation, or changes to AGENTS.md.

## Environment

- Wrapper: Kotlin Toolchain 0.12.2, upstream commit `82a15324c`, distribution
  SHA-256 `9fec42de4f378a27240b78c1b66ffef0c2aca66a4a6390ca67fe51aacb75677e`.
- Android CLI: 1.0.16261425. Always use `android --no-metrics`.
- SDK: `/home/amber/.local/share/android-sdk`; API 36 revision 2 installed with
  `android --no-metrics sdk install platforms/android-36` through approved access.
- Toolchain provisions command-line tools 22.0 and uses build tools 37.0.0.
  Its Android backend delegates to Gradle 9.5.0/AGP 9.3.1; this is an internal
  implementation detail, not a user-approved migration to Gradle.
- Wrapper defaults: Kotlin 2.4.10; Compose Multiplatform catalog 1.11.1 resolving
  Android Compose UI/foundation 1.11.2 and Material3 1.5.0-alpha17.
- All final modules pin JDK 21 (Temurin 21.0.12.1+1); JVM tests use JUnit Platform
  6.1.3. The initial template provisioned default Temurin 25.0.4.1+1 before pinning.
- Room runtime/compiler 2.8.4, KSP2 2.3.11, SQLCipher Android 4.17.0,
  AndroidX SQLite 2.6.2, Activity Compose 1.13.0, AndroidJUnitRunner 1.7.0,
  Compose UI test 1.11.2. SQLCipher 4.19.0 was rejected at APK packaging because
  its AAR requires compile API 37; 4.17.0 preserves the agreed API 36 configuration.
- Explicitly selected ONEPLUS_A6013: Android 15/API 35, 4096-byte pages.
  Debug app, separate test APK and temporary signed release installed under user 0.
  No emulator was created or started. No other app's data was changed.

Commands are run from the workspace root with existing default cache settings.
Sandboxed Android builds failed on
`/home/amber/.local/share/android-sdk/cmdline-tools;22.0.lock: Read-only file system`.
Approved execution passed that restriction but exposed an inherited environment
conflict:

```text
AndroidLocationsException: Several environment variables and/or system properties
contain different paths to the Android Preferences folder.
ANDROID_SDK_HOME: /home/amber/.local/share/android-sdk
ANDROID_USER_HOME: /home/amber/.local/share/android-sdk/usr
It is recommended to use ANDROID_USER_HOME as other methods are deprecated
```

The user removed `ANDROID_SDK_HOME`; new commands confirmed it absent while
`ANDROID_USER_HOME` was retained. Builds then passed without command-scoped
environment workarounds. The SDK writable root was expanded by the user.

## Capability Matrix

| Capability | Command or evidence | Observed result | Custom integration and remaining uncertainty |
| --- | --- | --- | --- |
| Pinned scaffold | `kotlin init --target-dir toolchain-proof`; `./kotlin --version` | Compose template generated; wrapper pinned and checksum verified by launcher | Generated wrappers moved to root; root `project.yaml` lists probes |
| JVM tests | `./kotlin test -m proof-domain` | 2 tests pass; exact 0/2/3-scale amounts, beyond floating-point precision, rejects rounding/overflow | Small pure Kotlin probe; no Android behavior asserted |
| Compose debug APK | `./kotlin build -m toolchain-proof -m proof-instrumentation`; explicit `adb install --user 0 -r` | Build/install pass; device Compose assertion adds sample | Shared `proof-ui`, thin debug app; synthetic probe state only |
| Variant IDs/isolation | Rejected `settings@debug` experiment; `node scripts/check-proof-apk.mjs APK SDK mode` | Debug ID `moe.frost.kochenite.debug`, temporary release ID `moe.frost.kochenite.debug.releaseproof`; debug code/resource markers present only in debug | Separate app modules; permanent `moe.frost.kochenite` reserved for durable key, not yet installed |
| Built-in Android tests | `./kotlin show tasks`; pinned `JvmTestTask.kt` and `Runner.kt` | Android tests execute on host with mockable Android JAR; no test APK task or runner configuration found | Host tests are not device instrumentation |
| Separate instrumentation APK | `node scripts/run-proof-tests.mjs "$ANDROID_SERIAL"` | 2 device tests pass with sanitized JSON and exit 0; initial failed assertion produced exit 1 | Ordinary app module plus instrumentation manifest; fixed target, 120-second limit; no orchestrator or standard androidTest integration |
| Room/KSP2/schema export | `./kotlin build -m proof-storage` and consuming app builds | Generated implementation compiles; version-1 JSON exported under `proof-storage/schemas` | Declarative KSP options; DAO methods are suspend; migrations deferred until gate |
| Encrypted storage | `EncryptedStorageDeviceTest` through runner above | Synthetic record with amount `9007199254740993` preserved through close/reopen; SQLCipher integrity returns no errors; file lacks plaintext SQLite header | Maintained SupportOpenHelperFactory integration; known test key only; wrong-key/plain-SQLite rejection, atomicity, Keystore and recovery remain foundation tests |
| Signing | `node scripts/create-proof-key.mjs`; `./kotlin build -m proof-release -v release`; `apksigner verify --verbose --print-certs APK` | Signed release verifies with APK v2 signature and RSA 3072 temporary key | Uses documented signing properties; script refuses existing key directory; key/password local and restricted; no permanent key or backup yet |
| Release shrinking | Release build above; `node scripts/run-release-proof.mjs "$ANDROID_SERIAL"` | R8 mapping produced, APK non-debuggable; real release inserts sample and preserves count/amount after force-stop/relaunch | No custom keep rules needed for these dependencies; ADB/JSON-layout journey checks actual shrunk app; not N-to-N+1 durable-key continuity |
| Native packaging | `node scripts/check-proof-apk.mjs APK SDK debug` and `release` | Both APKs pass signature, `zipalign -c -P 16 4`, all 8 packaged ELF libraries' LOAD alignment/congruence >=16 KB, source/resource isolation, SDK/permission checks | Checks SQLCipher 4.17.0 and Compose graphics native libs on 4 ABIs; only API 35/4 KB runtime tested |

No unverified capability counts toward Phase 1 completion. Light presentation and
system-bar clearance were visually inspected on the phone. Dark mode, large text,
TalkBack operation, other devices/OS versions, and 16 KB runtime remain unverified.
An early black screenshot taken while locked was rejected as visual evidence.

## Source Evidence

Inspected upstream at the exact wrapper commit, using a read-only reference
checkout under `/tmp/kochenite-toolchain-source`:

- [Android settings schema](https://github.com/JetBrains/kotlin-toolchain/blob/82a15324c/sources/frontend-api/src/org/jetbrains/amper/frontend/schema/androidSettings.kt)
- [Host JVM test task](https://github.com/JetBrains/kotlin-toolchain/blob/82a15324c/sources/amper-cli/src/org/jetbrains/amper/tasks/jvm/JvmTestTask.kt)
- [Android backend test phase](https://github.com/JetBrains/kotlin-toolchain/blob/82a15324c/sources/android-integration/amper-android-runner/src/org.jetbrains.amper.android/Runner.kt)
- [Signing and shrinking docs](https://github.com/JetBrains/kotlin-toolchain/blob/82a15324c/docs/src/user-guide/product-types/android-app.md)
- [KSP options](https://github.com/JetBrains/kotlin-toolchain/blob/82a15324c/docs/src/user-guide/advanced/ksp.md)
- [Public extension limits](https://github.com/JetBrains/kotlin-toolchain/blob/82a15324c/docs/src/user-guide/plugins/overview.md)
- [Android AAR packaging limitations](https://github.com/JetBrains/kotlin-toolchain/blob/82a15324c/sources/amper-cli/src/org/jetbrains/amper/tasks/android/AndroidAarTask.kt)
- [Debug packaging inputs](https://github.com/JetBrains/kotlin-toolchain/blob/82a15324c/sources/amper-cli/src/org/jetbrains/amper/tasks/android/LastPhaseAndroidDelegatedGradleTask.kt)
- [SQLCipher 4.17.0 integration](https://github.com/sqlcipher/sqlcipher-android/blob/v4.17.0/README.md)

## Device and Evidence Conventions

Rediscover the phone before each session:

```sh
adb devices -l
adb -s "$ANDROID_SERIAL" get-state
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk
adb -s "$ANDROID_SERIAL" shell getconf PAGE_SIZE
```

Set `ANDROID_SERIAL` explicitly to the intended connected phone; do not select
arbitrarily among devices. Do not use `./kotlin run`: its task graph includes
emulator/system-image provisioning. Install verified APKs explicitly through ADB.
The test component is
`moe.frost.kochenite.debug.test/androidx.test.runner.AndroidJUnitRunner`.
Its target is fixed to `moe.frost.kochenite.debug`; it cannot target Bluecoins.

Build logs and future device evidence stay ignored under `build/` or `.local/`.
Capture only Kochenite screens. Avoid unrestricted logcat dumps or private app
data. Future release upgrades use `adb -s "$ANDROID_SERIAL" install -r` with the
same signing key; never clear data to fix a failed upgrade.

Phase 3's local recognition tunnel will be:

```sh
adb -s "$ANDROID_SERIAL" reverse tcp:8787 tcp:8787
```

No recognition service or cleartext exception exists in this proof.

## Repeatable Commands

Prerequisites: JDK/keytool and Node.js on PATH, SDK API 36/build tools 37.0.0,
`adb`, `android` CLI, `unzip`, `readelf`; existing default cache access. First-time
release setup uses `node scripts/create-proof-key.mjs`. It generates only a 30-day
temporary proof certificate. Never use it for the real release application.

Run these commands sequentially. Concurrent CLI build processes in this same
checkout raced on shared KSP output and produced a FileNotFoundException; that
attempt is not counted as a successful clean build.

The final sequential clean-output debug/test and release builds passed, followed
by both JVM tests, both real device instrumentation tests, APK checks, and the
release add/force-stop/relaunch journey. One release journey attempt failed on an
empty Android CLI layout response after instrumentation. The runner now retries
empty responses within its existing eight-attempt limit; the rerun passed.

```sh
./kotlin clean
./kotlin build -m toolchain-proof -m proof-instrumentation
./kotlin build -m proof-release -v release
./kotlin test -m proof-domain
node scripts/check-proof-apk.mjs build/tasks/_toolchain-proof_buildAndroidDebug/gradle-project-debug.apk /home/amber/.local/share/android-sdk debug
node scripts/check-proof-apk.mjs build/tasks/_proof-release_buildAndroidRelease/gradle-project-release.apk /home/amber/.local/share/android-sdk release
adb -s "$ANDROID_SERIAL" install --user 0 -r build/tasks/_toolchain-proof_buildAndroidDebug/gradle-project-debug.apk
adb -s "$ANDROID_SERIAL" install --user 0 -r build/tasks/_proof-instrumentation_buildAndroidDebug/gradle-project-debug.apk
adb -s "$ANDROID_SERIAL" install --user 0 -r build/tasks/_proof-release_buildAndroidRelease/gradle-project-release.apk
node scripts/run-proof-tests.mjs "$ANDROID_SERIAL"
node scripts/run-release-proof.mjs "$ANDROID_SERIAL"
```

`--user 0` above was verified with `am get-current-user` for this phone; rediscover
the intended user before installing elsewhere. Phone must be unlocked. The release
journey uses Android CLI's layout instrumentation server, installed on first use.
Both journeys add synthetic records; they do not reset retained databases. Only
the storage test's uniquely named disposable database is deleted after its test.
Tests do not use user financial data. The runner rejects missing/offline serials,
missing expected packages and incomplete/failed assertions. Device results live in
`.local/proof-evidence/`; host reports/logs live in `build/`. Clean removes host
outputs, so retain deliberately selected sanitized evidence before cleaning.

The public temporary certificate SHA-256 fingerprint is
`ec930b6b3a18d885930a67920318802ae9bee156dbca5abb5dd29dd59325f539`.
No private key/password is committed. The permanent signing-key backup/recovery
procedure and actual user-controlled off-machine backup remain after the gate.

## Next Action

Obtain the user's explicit build-system choice. Then implement the remaining
[Phase 1 milestones](../.agents/plans/phases/01-foundation.md), including production
key management, failure preservation, migration/atomicity tests, ViewModels,
durable signing and upgrade continuity. Phase 2 must not start until all Phase 1
exit criteria have evidence. The disposable proof database is not the financial schema.
