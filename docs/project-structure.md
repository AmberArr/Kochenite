# Project structure

Keep the existing Gradle modules. Features are packages within `app`; extracting
more modules now would add build boundaries without a demonstrated need.

| Location | Responsibility |
| --- | --- |
| `app/.../MainActivity.kt` | Compose entry point and storage-to-domain dependency wiring |
| `app/.../BookViewModel.kt` | Book startup, shared settings, overview and cross-feature refresh |
| `app/.../navigation` | Typed routes, destination composition and route-scoped completion |
| `app/.../transactions/editor` | Transient editor state, posting lifecycle and editor UI |
| `app/.../transactions/list` | Query generation, paging, selection and transaction list UI |
| `app/.../transactions/detail` | Posted transaction detail and its actions |
| `app/.../accounts` | Account/group mutation state and forms |
| `app/.../setup`, `settings`, `overview`, `templates` | Corresponding feature screens |
| `app/.../ui/components` | Shared form controls with no persistence dependency |
| `domain/.../editor` | Android-independent editor input and pure transformations |
| `domain/.../ledger` | Financial rules, values and repository interfaces |
| `persistence/.../ledger/RoomLedger.kt` | Atomic posting and token-checked mutations |
| `persistence/.../ledger/RoomLedgerReader.kt` | Snapshot-consistent reads and batched aggregate loading |
| `persistence/.../ledger/RoomTemplates.kt` | Template persistence |
| `persistence/.../ledger/entities`, `dao`, `migrations` | Room declarations, SQL and schema history |
| `recognition-contract` | Existing recognition contract boundary; recognition implementation remains deferred |

Paths abbreviate `src/main/kotlin`. The three Room declaration directories keep
the existing Kotlin package intentionally: `LedgerDatabase` retains its qualified
name and schema history. This restructuring does not change schema version 7.

`MainActivity` supplies `BookServices` to `BookViewModel`. Feature state owners
receive the narrow repository interfaces they use and the ViewModel coroutine
scope. This allows host tests with delayed fake repositories. Ordinary editor
input stays in memory; it is never stored in navigation arguments or saved state.

Editor completion has one navigation owner: the editor route reacts to its state
being cleared. Other form completions carry the originating back-stack entry ID,
so a delayed completion cannot pop a different destination. Paging reserves the
cursor before launching, invalidates old query generations, cancels old requests,
and merges entries by ID. Changing search scope clears bulk selection; preview
and submission use the same visible row snapshot.

Each populated ledger page uses one entry query and four relation batches inside
a Room transaction, rather than four relation queries per entry. Book refresh no
longer loads posted and trash pages in addition to the active search. Text input
is debounced for 200 ms. Substring text matching still scans candidate rows;
50,000-record performance and an index preserving the chosen Unicode/substring
semantics remain explicit Phase 2.9 work, not a claimed optimization result.

Unexpected storage exceptions have a separate result from recovery-required
storage damage. Diagnostics record only the exception class, never financial
payloads or exception messages. Cancellation continues to propagate.

Host regressions live in `app/src/test/kotlin/transactions` and
`domain/src/test/kotlin/editor`. Financial instrumentation lives in
`persistence/src/androidTest/kotlin`; app UI and separate-process checks live in
`app/src/androidTest/kotlin`. See [financial kernel notes](financial-kernel.md)
for commands and the [worklog](worklogs/02-manual-bookkeeping.md) for evidence.
