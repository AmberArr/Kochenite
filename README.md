# Kochenite

Native, local-first Android bookkeeping project. Gradle was selected after the
Phase 1 Toolchain proof; this repository contains a **synthetic-only proof**,
not an app ready for financial records.

- [Current Gradle build and test commands](docs/gradle-build.md)
- [Historical Toolchain capability matrix](docs/toolchain-proof.md)
- [Master plan](.agents/plans/PLAN.md)
- [Phase 1 plan and verification status](.agents/plans/phases/01-foundation.md)

Use the pinned `./gradlew` wrapper from the repository root with default caches.
Only physical-device testing is permitted on this host. No recognition service
or financial ledger is implemented yet.
