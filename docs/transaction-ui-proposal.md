# Transaction list and editor proposal

September 24, 2026. Authorized for implementation in this session. Scope: the
Transactions list and Create/Edit Transaction. It follows the latest direction
to use Expense, Income and Transfer kinds, with Expense/Income direction carried
by the signed amount, and to treat related-transaction references as general
context rather than refund-only behavior. The accepted reference contract is
multiple links to posted transactions of any kind, preserved while editing and
cleared when copying or applying a template. A trashed target remains readable
through an existing link; permanently purging a target detaches links and
invalidates affected edits. Other app destinations are unchanged.

## Evidence and critique

Reviewed `BookScreen.kt`, relevant editor actions in `BookViewModel.kt`, Phase 2
requirements, and the September 23 physical-phone screenshots in ignored
`.local/ui-review-2026-09-23/`. The current Git working tree has no application
changes since that review. On September 24, Bluecoins was inspected read-only
on the connected OnePlus 6T. Its Transactions page has a compact search/filter
toolbar, a period heading with a right-aligned total, and flat rows pairing a
strong trailing amount with transaction/category identity and quieter account
metadata. Its Add action opens the entry form directly. The form keeps name,
date/time and a large amount control prominent, with account/category, split,
label and note controls below and transaction-kind/save actions anchored at the
bottom. It initially focused Name, which is not adopted because Kochenite's
agreed manual-entry priority is Amount. Opening a posted row, then Edit, took
two taps to reach editing; detail emphasizes payee, date/time, total and
allocation/category rows. These density and hierarchy patterns inform the
proposal; Kochenite retains its own Material palette and explicit textual kind
labels. Only screen navigation and an empty, untouched Add form were used; no
Bluecoins entry was edited or saved.

- The ledger's full filter form occupies the initial viewport; transaction rows
  appear much farther down. Status buttons have no selected treatment.
- Rows concatenate date, merchant and amount into one text button. Account,
  category, time and transaction kind are hard to identify or absent. Draft rows
  omit amounts, making resumption unnecessarily difficult.
- The editor is nominally amount-first, but type selection precedes amount and
  optional merchant-currency fields precede category. Even a single category
  appears inside an allocation editor with duplicated amount controls.
- Notes are single-line; every available tag becomes a checkbox row. Posting and
  discard sit at the bottom of this growing form. Global Add remains visible
  inside the editor and competes with finishing the current entry.
- Existing advantages to retain: explicit posting, truthful saving feedback,
  labeled fields, visible reconciliation, Material surfaces and draft recovery.
- The current implementation still has a separate Refund kind and a
  purchase-only link/action. The proposal replaces that interaction model with a
  positive Expense and a general related-transaction reference; no app behavior
  has changed yet.

## Transaction list

Use a plain ledger surface, date headers and subtle row dividers. No per-row
cards, merchant avatars, alternating backgrounds or colored status pills.

Local page structure: compact Transactions title/action row; status tabs
Posted / Drafts / Incomplete / Trash; compact search with trailing Filter action;
one line describing active filters when present; then the ledger. Search and
filters are offered only for Posted and Trash, preserving current query scope.
The selected tab has both an indicator and selected semantics. Tabs can scroll
at large text rather than shrinking labels. Templates and Bulk categories move
to the page overflow menu. Bulk categories remains available only in Posted.
The existing persistent Add opens the editor. Do not redesign the global shell.

Posted/Trash entries use the existing newest-first query order. Insert a header
at each date boundary: Today · Thu, 24 Sep; Yesterday; older calendar dates,
including year when needed. Continue groups across loaded pages, preserving
existing Load more behavior. Do not add daily totals. Drafts/Incomplete retain
their existing repository order and show the transaction date within each row;
do not relabel that ordering as last edited or change queries to group it.

At default text size, an ordinary row is approximately 72–80dp tall:

```text
Today · Thu, 24 Sep
Corner Market                              −USD 24.80
Visa · Groceries · 18:42
───────────────────────────────────────────────────────
Station Shop                                 +USD 8.20
Visa · Travel · 12:10
Merchant amount JPY 1,200
```

Merchant/payee and actual account-currency amount are the primary pair. Amounts
are right aligned, semibold, with tabular numerals where supported; always show
the currency code. Title is medium weight, one line at ordinary size. Below it,
show account, category and time as quiet metadata. Do not repeat Expense or
Income as a row label: the signed amount, category and merchant already make an
ordinary entry scannable. Use parent/child category context when needed to
disambiguate names. If merchant is empty, use a useful existing identity such as
category or account context rather than a generic kind label.
Notes and tags remain in detail/editor; no tag-chip cloud or note excerpts.

For ordinary entries, the category identifies what the transaction is for and
the signed amount shows direction; do not add Expense or Income row labels. A
negative Expense is spending; a positive Expense is a credit/refund that offsets
spending in its posting period. Positive Income adds income; negative Income
reverses it. Do not add a Refund label or use color alone to signal a refund.
Related transactions are ordinary secondary context, with no special
amount styling or reporting effect from the reference itself. Transfers are
identifiable from the source → destination account pair, with separately labeled
Sent/Received amounts when needed; cross-currency values stay separate. Do not
add a redundant Transfer label. Treat account balance changes as a dedicated,
balance-only Adjustment kind/family, separate from Expense and Income. Use
“Opening balance” for an account's initial balance and “Balance adjustment” for
a later correction, with the account and signed amount clearly visible. This
keeps the opening/correction distinction readable while grouping both as
balance-only records. Use neutral amount text; teal remains an interaction
accent.

One optional third line holds relevant context: merchant amount when it differs
from the actual charge, an incomplete warning, or a short split/item summary.
Use text such as “3 category splits” or “4 receipt items” instead of pretending
there is one category. Merchant amounts are labeled context, never a second
posting. If a row needs multiple distinct facts, let it grow rather than hide
an amount or uncertainty. At large text, place the entire amount on its own
right-aligned line and allow metadata to wrap; never ellipsize monetary values.

Drafts use the same title/amount hierarchy with a visible Draft label. Known
amounts remain visible; missing or malformed raw amounts say “Amount needed”
or “Check amount,” never zero. Incomplete rows show Incomplete and identify the
missing actual amount; transfer rows can show “Received: unknown.” Both views
explain once that their entries are not posted. Edit drafts can say “Editing
existing transaction.” Trash rows remain readable with a Trash label.

Filter opens a Material bottom sheet containing the current date range, account,
category, tag, kind and currency-specific amount controls. Use date pickers with
text input and ordinary selectors. Preserve current filter semantics, including
clearing amount bounds when amount currency changes. Valid edits update the
existing query; Done merely closes the sheet. Invalid input has a local error
and must not be presented as “no matching transactions.” Reset clears filters
explicitly. The list shows a readable active-filter summary and Reset action.
Switching to Drafts/Incomplete hides search/filter controls but preserves their
state for return to Posted/Trash.

Empty states distinguish “No posted transactions yet” with Add, “No matches”
with Clear search/filters, “No saved drafts,” “No incomplete transactions,” and
“Trash is empty.” Loading and query failures use their own feedback and Retry,
not an empty ledger message.

## Transaction editor

Use the existing editor route with its own task scaffold: Back, New transaction
or Edit transaction, and overflow for Discard draft. Hide the global Add and
primary navigation while this task is open. Back uses the existing flush/save
behavior. No wizard and no separate review step for an ordinary entry.

```text
‹ New transaction                                  ⋮
Expense ▾                              Draft saved

USD                           24.80
Account                       Visa                 ›
Merchant/payee                Corner Market
Category                      Groceries            ›
Date/time                     Today · 18:42         ›
Allocation details · 2 rows                       ›
More details                                      ⌄

                                   [Post transaction]
```

The compact type selector provides Expense / Income / Transfer without a
separate Refund choice or an obligatory selection step. Expense and Income keep
the sign the user enters: negative Expense is spending, positive Expense is a
credit, positive Income is income, and negative Income reverses income. Keep
Adjustment out of this ordinary-entry selector: opening balances are entered
while creating an account, and later balance corrections use the existing
account adjustment flow. Both appear in the list as balance-only records, not
as income or spending.
Amount is the first input and the visual focal point, followed by account,
merchant/payee, category and
date/time. Preserve current defaults; do not introduce last-used account or
merchant suggestions through this presentation pass. Use the decimal keyboard
for ordinary amounts, retaining the current parser and precision rules. Only a
fresh create flow requests amount focus automatically; resume/edit should not
open the keyboard or jump past existing context unexpectedly.

Account/category use full-width 48–56dp selector rows with label, value and
chevron; their sheets show current selection and valid existing options.
Category selection keeps two-level labels and Uncategorized. Merchant stays an
inline text field. Date/time is one summary row opening date and time controls,
preserving calendar/local-time behavior. Picker cancellation retains the prior
value. No inline creation of accounts/categories is introduced.

More details is an inline collapsible section with Notes, Tags, Merchant amount
and Related transactions. Empty controls stay collapsed on new entries. Existing
values appear in the collapsed summary so they cannot disappear from view. Notes
become multiline; Tags opens a multi-select sheet, with selected names summarized
on return. Merchant amount reveals paired amount/currency fields and an explicit
context label. If present, show this merchant amount next to the actual amount
summary even when More details is collapsed. Related transactions are available
independently of transaction kind and remain contextual: adding a reference does
not set or change kind, sign, amount, category, posting or report values. Its
selection surface should stay within the editor flow, without adding a navigation
destination. A validation failure expands the affected section and focuses its
field without losing raw text.

The primary footer contains Post transaction, above system/keyboard insets.
Keep it reachable while scrolling; allow content to scroll fully clear of it.
Draft saved / Saving / Not saved stays in one quiet status area, with failures
prominent and actionable. “Saved” means draft saved, never posted. Back awaits
pending saves and remains in the editor on failure. Discard uses the current
discard action; navigation must not implicitly discard or post.

## Unified category and receipt rows

Use one allocation editor for category allocation and receipt detail. The data
still has two linked concerns: each allocation has a category and actual
account-currency amount; optional receipt detail adds a merchant-currency line
amount and an optional item title to each allocation row. The user should not
choose between separate “split mode” and “item mode.”

A normal one-category entry shows its existing Category selector on the main
form and no allocation amount field. “Allocation details” opens one focused,
full-screen child editor only when the user needs multiple rows or receipt
detail. Existing multirow entries show row count and reconciliation summary in
the parent instead of a category control that could overwrite row categories.
The child edits the same autosaved draft; Done returns to the parent, not posts
or discards. It is not a new destination or separately saved object.

Every row always has category and actual account-currency allocation. Rows
share one simple “Add row” action. Optional “Receipt details” reveals a title
and final-inclusive merchant-currency line amount on each row. Titles may be
blank; receipt amounts remain required for every row when receipt detail is in
use. Preserve the existing one-to-one row identity and receipt-total
reconciliation. This lets category-only rows behave as today's splits and rows
with receipt fields behave as today's receipt items, without a mode switch.
For long receipts, keep row summaries compact and expand one row's fields at a
time. Add/remove actions have accessible targets.

Keep allocated / actual charge and, when receipt details are enabled, receipt
lines / receipt total visible with their own currencies and textual Matched or
Mismatch state. Preserve proportional suggestions, rounding, manual
corrections and exact sums. No inferred balancing lines. Show a concise count
and reconciliation status in the parent, for example “3 rows · receipt totals
match.” Never hide a mismatch because only one row exists. Turning off receipt
details warns that titles and merchant line amounts will be removed; no data is
silently discarded. Opening or closing the child editor alone does not mutate
any row.

The current domain rejects blank receipt-line descriptions and requires receipt
lines to correspond one-to-one with allocations. Making the proposed title
optional therefore requires relaxing only the nonblank-text check. Keep the
one-to-one links, positive receipt amounts, receipt-total reconciliation,
account-allocation reconciliation and posting behavior unchanged.

## Important flows and state handling

| Flow | Recommended interaction |
|---|---|
| Ordinary expense | Add → focused amount → account if needed → merchant → category → date/time if needed → Post. No advanced section or separate save/review step. |
| Choose account/category | Tap summary row, tap existing option, return. Two taps each; unchanged defaults need no interaction. |
| Resume draft | Drafts → row → same editor. Known values and save status remain visible. Back preserves it. |
| Edit posted entry | Row → existing detail → Edit → editor. Retain this existing two-tap entry path; do not redesign detail or make a row tap mutate records. |
| Incomplete | Missing actual amount exposes Save incomplete separately from Post, using existing eligibility rules. Invalid text remains a correction error. Completing amounts still requires explicit Post. |
| Future date | Show a draft-only explanation, retain future date and existing posting validation. Do not create scheduling behavior. |
| Transfer | Type selector changes labels to Sent / From / To / Received. Show both currencies for cross-currency transfers. Keep existing same-currency amount coupling and transfer rules; hide category/splits. Merchant/payee becomes optional secondary metadata. |
| Expense credit and related transactions | Use the Expense type with a positive amount. Optionally reference a related transaction in More details; the reference is context only and does not choose kind, sign, amount or category. The same reference control is available independently of transaction kind. |
| Copy/template | Keep existing actions and routes; resulting editor visibly says draft and always needs explicit Post. Clear related-transaction references when copying or applying a template. |
| Bulk categories | Overflow → selection mode reveals allocation checkboxes beneath ledger rows. Show selected allocation count, existing currency-separated preview and category action. Preserve atomic/stale-selection rules. No whole-transaction selection shortcut. |

Tap counts above describe navigation, excluding typing and scrolling. The
read-only Bluecoins check measured one tap from its Add action to the form and
two taps from a transaction row through detail to Edit. Type changes that clear existing
split/item fields should explicitly disclose that consequence before calling
the existing action; no automatic migration or new financial interpretation.

## Compose layout specification and acceptance checks

| Component | Specification |
|---|---|
| TransactionList | LazyColumn, stable entry IDs and date-header keys, existing bounded paging, saved scroll/filter state. Sticky date headers must not cover focused rows. |
| TransactionRow | Whole row clickable; 16dp gutters, 8–12dp vertical padding, 8–12dp gap before trailing amount. 16sp title/amount, 14sp metadata, 12–14sp date label, all scaling with system settings. No Expense/Income label; transfer account pair and adjustment title identify those records. Minimum heights, never fixed heights. |
| Surfaces | Existing light/dark Material surface and onSurface roles. Subtle outlineVariant dividers. No card nesting, tinted row fills or per-kind background colors. |
| Form rows | 48dp minimum hit targets; compact read-only selectors, labeled Material fields only where typing is required. 8dp local gaps, 16–24dp between meaningful groups. |
| Amount input | Approximately 32sp at normal text size; visible account currency, no scale-down to fit. Grow/reflow for large values or fonts. |
| Allocation row | Category + account-currency allocation always; optional merchant-currency line amount and optional title under receipt details. One row identity; no duplicate collections. |
| Sheets/panels | Material selectors and filter sheet; full-screen modal for repeated allocation rows. Shared draft identity, separate UI focus/expansion state; close before parent Back. |
| Editor footer | One primary Post button; conditional Save incomplete action. IME-aware, scroll clearance based on actual footer height. No floating Add. |
| Accessibility | Selected-state semantics, labels for icons, combined understandable row announcements, 48dp targets, field-associated errors and textual reconciliation status. Avoid duplicate announcements from decorative icons. |

Later implementation should verify populated/empty/filtered lists, transfers,
opening balances and adjustments, positive Expense credits, negative Income reversals, unknown/invalid amounts, long names and large values, future and
edit drafts, single/multiple allocations, foreign receipt mismatches, keyboard
and Back, save failures, filter restoration and process recreation. Check both
themes and a compact phone at normal and large text. Do not infer coverage from
the existing foundation screenshots. Density must yield to readable values.

## Accepted financial and reference contract

Expense and Income store signed actual account-currency minor units. A negative
Expense reduces the account balance and increases spending; a positive Expense
increases the balance and offsets spending for its posting period. Positive
Income increases both balance and income; negative Income reduces both. Their
allocations remain nonnegative magnitudes and must sum exactly to the absolute
actual amount, including overflow checks. Receipt totals remain positive and
reconcile to their independent merchant amount. Transfers require positive sent
and received amounts. Opening balances and adjustments retain signed balance-
only behavior. Balance postings use the signed actual amount directly for
Expense and Income. These rules preserve existing balances and historical
report contributions when legacy positive EXPENSE values become negative and
legacy REFUND values become positive EXPENSE values.

References are ordered, distinct IDs, with a maximum of 100 per transaction.
New references can target any currently posted transaction, including another
expense credit or a transfer. Editing preserves eligible existing references;
trash does not erase them. Permanent deletion detaches inbound links. Copying
and applying a template clear them. A reference changes no posting, allocation,
amount, category or report. Old purchase links migrate as ordinary references.
Receipt line descriptions may be empty; line IDs, positive amounts, one-to-one
allocation links, account-currency reconciliation and merchant-currency
reconciliation remain required. Search scope, defaults and draft ordering remain
as implemented. No rates, aggregates, or destinations are introduced.
