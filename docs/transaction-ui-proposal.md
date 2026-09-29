# Transaction list and editor

This is the accepted editor behavior and visual reference for Phase 2 manual
transactions. Recognition review remains a separate, deferred workflow that may
use reviewable extraction drafts. Ordinary manual transaction input is transient
until Save transaction succeeds.

## Save and discard

- Keep new, copied, template-applied and posted-edit input in the ViewModel only.
  Configuration changes and temporary backgrounding retain it while that
  ViewModel survives. Process termination loses unsaved input; navigation state
  may retain route identifiers only and must never reopen an abandoned form.
- An unchanged form closes directly. Back from a changed form asks “Discard
  changes?” with Keep editing and Discard. Closing the item sheet or item screen
  retains its input in the parent form.
- A posted aggregate and all its postings, allocations, items, tags and references
  stay effective until the complete replacement validates and commits atomically.
  Validation, stale-token or storage failures retain form input and the old record.
- Save transaction is the only editor submit action. A genuinely missing actual
  account amount or transfer side asks once whether to save explicitly under
  Incomplete. Validate all supplied values before that confirmation. Future dates,
  invalid currency precision, categories and item mismatches are errors.
- Incomplete entries never affect balances or reports. Reopening one creates an
  in-memory session. Leaving abandons only that session; the saved incomplete
  record remains. Completing consumes it and posts the aggregate atomically.
  Posted edits cannot become incomplete.
- Disable Save while a submission runs. Failures keep the form and show a useful
  message. There is no editor draft status, autosave, recovery, or discard menu.

## Form layout

Use the established semantic theme colors for both light and dark modes. Keep the
surfaces charcoal in dark mode, dividers thin, rounding modest and mint accents
restrained. Build a compact, full-width form with accessible 48dp controls and a
fixed Save transaction footer above keyboard and system insets.

Expense and Income fields appear in this order:

1. Back, then New transaction or Edit transaction.
2. Expense / Income / Transfer tabs, with an underline on the selected tab.
3. Date/time and the nonfunctional From receipt control.
4. Underlined Title with a clear action.
5. Large right-aligned Amount: sign, currency symbol, number and currency picker.
6. + Split transaction, or the clickable item count and reconciliation summary.
7. Account, then Category only while there are no explicit items.
8. Note, Tags, Related transactions and a nonfunctional Attachment row.
9. Fixed Save transaction footer.

Focus Title on fresh creation. Existing and resumed entries do not open the
keyboard. Note opens a multiline sheet; Tags and Related transactions use their
selection sheets. Populated rows show useful summaries. Do not include a More
details group or routine save-status message.

New Expense starts negative. Selecting Expense resets the sign to negative;
Income resets it positive. The sign button can override either choice. Both
amounts use that direction. Label the first field Amount. When transaction and
account currency differ, show Account amount directly underneath, fixed to the
selected account's currency.

Changing transaction currency keeps the typed transaction number without
conversion and clears Account amount on first separation. Changing to an account
with another currency while one amount is shown switches that field to the new
account currency and clears it. With two amounts, changing the account preserves
Amount and clears Account amount. When the currencies match, merge the fields and
keep the transaction amount. Currency changes never fetch rates or round numbers;
reject unsupported precision visibly. Item inputs keep their numeric text across
currency changes and must be revalidated.

Transfers keep the kind tabs, date, title and optional rows. Show labeled Sent and
Received amounts above From and To account selectors. Amount currencies follow
their accounts. Same-currency amounts synchronize; cross-currency amounts are
independent. Hide items, Category and the sign button.

## Transaction items

Open a full-screen Transaction items page with Back/title, transaction title/date
context, compact divided rows, Add item, and a fixed item count, sum/target,
matched/mismatch feedback and Done footer. Done returns to the unsaved parent.
There is no page overflow action.

Each row has a functioning drag handle, primary title or category when title is
blank, secondary category when a title exists, right-aligned transaction-currency
amount and a separate X delete action. Row taps edit the item; drag and delete
must not trigger that tap. Expose accessible Move up and Move down actions.

The item sheet has optional Title, Category and Amount in transaction currency.
Changes update only the parent ViewModel. Closing keeps partial changes in the
session; Done closes the sheet. There is no database autosave, receipt toggle or
editable account allocation.

Split transaction opens the items page without creating an item or opening a
sheet. Add item creates an untitled, blank-amount, Uncategorized item; the first
item then replaces the implicit ordinary allocation. X deletes immediately and
offers Undo that restores identity, content and position. Deleting the final
item restores the ordinary Category picker with its category and leaves the
transaction total unchanged. Save persists item order.

Keep transaction total independent of item edits. An unfinished, invalid or
missing value is pending, never matched. Same-currency allocation follows item
amounts. Foreign allocation uses the existing exact proportional
largest-remainder calculation when items and actual account amount are valid;
recalculate after changes to items, actual charge or order. Missing inputs stay
pending. Never insert a balancing item or infer an account charge.

## Storage and verification

Direct posting accepts complete input, a stable submission identity and the
expected posted-record token for replacement. It validates and replaces postings,
allocations, items, tags and references inside one database transaction. Explicit
incomplete records have read/save/delete operations and concurrency tokens.
Completing one posts and consumes it inside that same transaction. New input,
copying and template application are read-only or in-memory. Opening balances and
account adjustments use the same direct posting boundary.

Reuse the Room drafts table only for explicitly saved incomplete entries; keep
legacy SQL names if useful. Separate EditorInput from the persistent incomplete
record type. Preserve template payload compatibility and add only optional,
backward-readable payload values. Keep every item tied to one allocation by the
existing identity. Ordinary transactions retain their implicit category
allocation. No automatic data reset or new network API is part of this workflow.

Visual acceptance proceeds on the physical phone in small increments: header and
tabs; date and title; amount controls; form rows; item list; item sheet; Save and
discard dialogs. Then verify no writes before Save, unchanged and changed Back,
ViewModel rotation retention and process-death loss, explicit incomplete
save/update/delete/completion, stale and failed saves, duplicate submissions,
future dates, signs, currency transitions and 0/2/3-decimal precision. Exercise
item creation, blank and invalid fields, one-item Category behavior, Undo,
reordering, exact allocations and reconciliation. Review both themes, large text,
long values, keyboard clearance, touch targets and accessibility actions. Finish
with `./gradlew verify` and the relevant physical-device suites after visual
acceptance.
