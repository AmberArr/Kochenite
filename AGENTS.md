# Working Instructions

## Plans and Session Workflow

- The intended master plan is `docs/plans/PLAN.md`, with detailed plans in `docs/plans/phases/01-foundation.md`, `02-manual-bookkeeping.md`, and so on.
- Keep per-phase worklogs in `docs/worklogs/01-foundation.md`, `02-manual-bookkeeping.md`, and so on, linked from their phase plans. Plans describe intended work and current decisions; worklogs record what happened.
- At the start of each session, read the master plan, current phase plan and latest entries in its worklog, inspect the code and Git status, and continue from recorded progress.
- Keep product scope, shared architecture, cross-phase decisions, dependencies, and phase status in the master plan. Link to the detailed phase plans.
- At the start of a phase, create or refine its detailed plan based on the actual code. Include deliverables, implementation steps, data/API changes, tests, and explicit exit criteria. Do not duplicate shared architectural rules.
- Resolve routine engineering choices independently. Ask about unresolved product choices or material changes to agreed scope or architecture. Routine phase planning does not require a separate approval step before authorized implementation.
- Implement in verifiable increments. Complete the current phase's exit criteria before starting the next authorized phase.
- Before ending a session, append a dated worklog entry with completed work, commands/results, verification gaps and encountered blockers. Keep only current status, unresolved limitations/blockers, exit-criterion completion and the next concrete action in the phase plan, linking to worklog evidence. Do not accumulate session narratives or resolved failures in plans. Update the master plan only for phase status and cross-phase decisions.
- Follow the user's latest instructions when they supersede a plan, and update affected plans to reflect the decision.

## Build and Tooling

- Use native Kotlin and Jetpack Compose with Gradle Kotlin DSL. The user selected Gradle after the Kotlin Toolchain 0.12.2 proof. Use the pinned `./gradlew` wrapper.
- Run build commands from the workspace root using the tools' default cache settings and existing host environment.
- If sandbox permissions or default tool/cache paths block a command, report the exact error and affected path, then ask the user to adjust the configuration or authorize the required access. Do not introduce cache redirection or environment workarounds on your own.
- Keep caches and generated build output out of Git.
- Use standard Android variants and `androidTest` instrumentation. Keep the domain independent of Android; future iOS/Multiplatform targets remain deferred.
- Use pinned Spotless/ktlint for Kotlin and Gradle Kotlin DSL: `./gradlew spotlessApply` formats, `./gradlew spotlessCheck` checks, and `./gradlew verify` runs formatting, Android lint, JVM tests and host script tests. Device tests remain explicit.
- The check-only hook in `.githooks` checks staged whitespace and runs Spotless for Kotlin/build/config changes. It refuses unstaged changes in those files rather than modifying the index. Enable it per checkout with `git config --local core.hooksPath .githooks`; preserve any existing hook configuration.

## Android Devices

- Always pass `--no-metrics` to Android CLI commands, for example `android --no-metrics --version`, to disable telemetry and avoid writes to the analytics spool.
- This machine does not support KVM. Never create or launch an Android emulator; use the remote physical phone over ADB.
- Rediscover connected devices using `adb devices -l`. Select the intended device explicitly with `adb -s "$ANDROID_SERIAL"`; do not hardcode an old wireless serial or choose arbitrarily among multiple devices.
- Use ADB reverse forwarding for local recognition-service access. Permit localhost cleartext traffic only in debug builds.
- Preserve Bluecoins records and all other apps on the phone. Reference inspection must not save changes, clear data, or uninstall apps.
- Use synthetic records and a separate debug application identity for destructive tests. Do not clear the user's bookkeeping data to solve a development problem.
- Distinguish host/rendering checks from real-device validation. Report disconnected-device or coverage limitations honestly.

## Finance and Recognition

- Keep bookkeeping local and usable offline without sign-in. Cloud recognition must not become a prerequisite for manual entry.
- Use integer minor units for finalized monetary amounts and decimal arithmetic for rates, quantities, and intermediate calculations. Never use floating-point money.
- Preserve original currencies and historical conversion snapshots. Account postings determine balances; category allocations determine spending. Do not double-count receipt lines, transfers, or credit-card repayments.
- Save financial aggregates atomically and verify reconciliation invariants. Do not silently invent amounts to balance an extracted receipt.
- All receipt and notification extraction produces reviewable drafts. Only explicit user confirmation posts financial transactions.
- The supplied recognition demo is in `receipt-ai-demo/a.js` and `receipt-ai-demo/prompt.txt`. Treat it as a provider-integration reference, not a validated contract. Consult it when planning recognition work; do not assume the demo is still pending.
- Keep deterministic fixture recognition for development and tests. Separate provider calls from schema validation, financial reconciliation, and ledger posting.
- Keep provider credentials on the gateway, never in the APK. Exclude secrets, private receipts, notification bodies, and financial records from commits and routine logs. Use synthetic or deliberately selected test inputs for external recognition calls.

## Verification and Git

- Inspect existing code and use its established patterns. Preserve user files and unrelated changes, including local skills and demo files; do not stage them incidentally.
- Run checks appropriate to the change. Prioritize meaningful tests for financial invariants, migrations, recovery, extraction failures, and device workflows. Documentation-only changes do not require app builds.
- Do not mark a phase complete until its exit criteria have evidence. Record checks that could not run and the reason.
- Committing is standing-authorized: commit without asking whenever a coherent increment is verified. Do not let verified work accumulate uncommitted across increments or sessions.
- Commit at these points: after each increment's relevant checks pass, before starting unrelated work, and before ending a session. Include the related plan and worklog updates in the same commit. Documentation-only changes need no app build before committing.
- Do not commit work whose checks fail or have not run. Leave it uncommitted and record its state and the missing checks in the worklog.
- Keep commits focused: split independent changes into separate commits. Inspect the staged diff and run `git diff --cached --check` before each commit.
- Stage explicit paths. Keep the user's `master` branch unless instructed otherwise. Do not push, rewrite history, revert unrelated work, or commit secrets without authorization.
- Git metadata may be read-only inside the sandbox even when source files are writable. If necessary, use the supported approval mechanism for authorized Git writes; do not delete or recreate `.git` as a workaround.
