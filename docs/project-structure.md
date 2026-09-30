# Project structure

Keep the existing Gradle modules. Features are packages within `app`; extracting
more modules now would add build boundaries without a demonstrated need.

| Location | Responsibility |
| --- | --- |
| `app/.../MainActivity.kt` | Compose entry point and storage-to-domain dependency wiring |
| `app/.../BookViewModel.kt` | Book startup, shared settings, overview and cross-feature refresh |
| `app/.../KocheniteTheme.kt` | Colour roles, finance and chart colours from the Figma variables |
| `app/.../navigation` | Typed routes, the navigation bar/rail shell, list-detail panes, storage-state screens and route-scoped completion |
| `app/.../transactions/editor` | Transient editor state, posting lifecycle, editor, items, sheets and date/time dialog |
| `app/.../transactions/list` | Query state, paging, running balances, selection, ledger rows, filter sheet and bulk mode |
| `app/.../transactions/detail` | Posted transaction detail and its actions |
| `app/.../transactions/EntryPresentation.kt` | Titles, icons and tones shared by rows, detail and templates |
| `app/.../accounts` | Account/group mutation state, account list and form, Groups & order |
| `app/.../reports` | Month report state and the Reports screen |
| `app/.../setup`, `settings`, `overview`, `templates` | Corresponding feature screens |
| `app/.../ui/components` | Shared components, form controls and money/date display with no persistence dependency |
| `app/src/main/res/drawable/ic_*.xml` | The Material icons exported from the Figma file |
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
so a delayed completion cannot pop a different destination. Sheets that save
(group, tag) and list-level actions stay on their screen. Each screen draws its
own app bar; the shell owns only the navigation bar or rail, the Add button and
the Undo snackbar. The Transactions query (`TransactionQuery`) lives in the
ViewModel so Overview and Reports can open the list with a tab or filters set.
Paging reserves the
cursor before launching, invalidates old query generations, cancels old requests,
and merges entries by ID. Changing search scope clears bulk selection; preview
and submission use the same visible row snapshot.

Each populated ledger page uses one entry query and four relation batches inside
a Room transaction, rather than four relation queries per entry. Book refresh no
longer loads posted and trash pages in addition to the active search. Text input
is debounced for 200 ms. After a posted page arrives, one more query reads the
postings of the listed accounts from the oldest listed entry onward and walks
back from current balances to give each row its running balance; its cost grows
with list depth. Substring text matching still scans candidate rows;
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
