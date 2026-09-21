# Kochenite

Native, local-first Android bookkeeping project. Gradle was selected after the
Phase 1 Toolchain proof; this repository contains a **synthetic-only foundation**,
not an app ready for financial records.

- [Current Gradle build and test commands](docs/gradle-build.md)
- [Release signing and recovery](docs/release-signing.md)
- [Historical Toolchain capability matrix](docs/toolchain-proof.md)
- [Master plan](.agents/plans/PLAN.md)
- [Phase 1 plan and verification status](.agents/plans/phases/01-foundation.md)

Use the pinned `./gradlew` wrapper from the repository root with default caches.
Run `./gradlew spotlessApply` to format Kotlin and `./gradlew verify` for host
checks. Enable the check-only hook with `git config --local core.hooksPath .githooks`
after checking for existing custom hooks; details are in the build guide.
Only physical-device testing is permitted on this host. No recognition service
or financial ledger is implemented yet.

The foundation uses plain Room/SQLite in private internal storage. It supports
sample insertion, restart-safe persistence and tested schema migration. It has
no database encryption or database-key management layer.
