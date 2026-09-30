# Kochenite

![AI Slop Level Over 9000!](https://img.shields.io/badge/AI_Slop_Level-Over_9000!-blue)

Native, offline-first Android bookkeeping app in active development. The app
supports local accounts and transaction entry; use synthetic data until the
remaining release and recovery gates are complete.

- [Project structure and state ownership](docs/project-structure.md)
- [Current Gradle build and test commands](docs/gradle-build.md)
- [Release signing and recovery](docs/release-signing.md)
- [Historical Toolchain capability matrix](docs/toolchain-proof.md)
- [Master plan](docs/plans/PLAN.md)
- [Phase 1 historical plan and verification status](docs/plans/phases/01-foundation.md)
- [Phase 1 historical worklog and verification evidence](docs/worklogs/01-foundation.md)

Use the pinned `./gradlew` wrapper from the repository root with default caches.
Run `./gradlew spotlessApply` to format Kotlin and `./gradlew verify` for host
checks. Enable the check-only hook with `git config --local core.hooksPath .githooks`
after checking for existing custom hooks; details are in the build guide.
See [financial kernel notes](docs/financial-kernel.md) for the implemented
ledger, storage boundaries, and remaining verification gates.
