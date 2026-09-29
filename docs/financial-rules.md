# Phase 2 financial rules and worked examples

These examples use synthetic records. All figures below are **integer minor
units**, with currency explicitly stated. An empty allocation set contributes
zero income/spending. Each example starts independently from the given balances.

## Equations

For each account, balance = sum of its signed postings for POSTED transactions.
For each currency and date range, an INCOME allocation contributes its signed
actual amount's direction: positive Income adds income; negative Income reverses
it. An EXPENSE allocation contributes its magnitude when the actual Expense is
negative and subtracts its magnitude when the Expense is positive. Thus
spending = negative-Expense allocations − positive-Expense credits;
net flow = income − spending. Receipt lines are explanations, never extra totals.
Net worth sums included accounts by currency, including archived and negative
accounts. Unposted recognition drafts, explicitly saved incomplete records and trash
rows contribute nothing. Opening balances and adjustments affect balances only. No
report converts historical amounts.

Expense and Income: one posting equal to the signed actual amount, with
nonnegative allocations summing exactly to its absolute magnitude. Negative
Expense is spending; positive Expense is a credit that offsets spending. Positive
Income is income; negative Income is an income reversal.
Transfer: different accounts, negative source −S and positive destination R,
no allocations; S = R when their currencies match. Cross-currency S and R are
independent actual amounts. Opening/adjustment: one signed posting, no allocations.
Zero openings/adjustments are valid. Expense/Income amounts must be nonzero;
transfers must have positive sent/received amounts.
No arithmetic may wrap; unrepresentable results are rejected explicitly.

Each account's currency is authoritative and permanently locks after its first
posting, even after trash or purge. A currency code has one consistent minor-unit
scale in the book. Transaction date is a calendar date and time is local clock
time, without timezone; confirmation rejects dates later than today's device date.
Null category means Uncategorized for that allocation's income/expense type.
Categories are two levels, with direct parent allocations permitted and counted once.

## Worked examples

| Case | Starting balances | Postings | Allocations | Ending balances | Income / spending / net flow |
| --- | --- | --- | --- | --- | --- |
| Ordinary expense (signed amount −1234) | Cash USD 10000 | Cash −1234 | Food USD 1234 | Cash USD 8766 | USD 0 / 1234 / −1234 |
| Income | Bank USD 0 | Bank +50000 | Salary USD 50000 | Bank USD 50000 | USD 50000 / 0 / 50000 |
| Negative account | Card USD −20000 | Card −3000 | Food USD 3000 | Card USD −23000 | USD 0 / 3000 / −3000 |
| Signed opening | Card USD 0 | Card −20000 | none | Card USD −20000 | USD 0 / 0 / 0 |
| Signed adjustment | Cash USD 10000 | Cash −250 | none | Cash USD 9750 | USD 0 / 0 / 0 |
| Zero opening | Bank JPY 0 | Bank 0 | none | Bank JPY 0 | JPY 0 / 0 / 0 |
| Lending | Cash USD 10000; Receivable USD 0 | Cash −4000; Receivable +4000 | none | Cash 6000; Receivable 4000 | USD 0 / 0 / 0 |
| Lending repayment | Cash USD 6000; Receivable USD 4000 | Receivable −1500; Cash +1500 | none | Cash 7500; Receivable 2500 | USD 0 / 0 / 0 |
| Card repayment | Bank USD 50000; Card USD −20000 | Bank −20000; Card +20000 | none | Bank 30000; Card 0 | USD 0 / 0 / 0 |
| FX transfer | Bank USD 10000; Cash JPY 0 | Bank −1000; Cash +1450 | none | Bank USD 9000; Cash JPY 1450 | both currencies 0 / 0 / 0 |
| Explicit fee after FX | Bank USD 9000 | Bank −25 | Fees USD 25 | Bank USD 8975 | USD 0 / 25 / −25 |
| Foreign purchase | Card USD 0; merchant JPY 1000 | Card −673 | Food USD 673 | Card USD −673 | USD 0 / 673 / −673; no JPY report amount |
| Incomplete purchase | Card USD 0; merchant JPY 1000, actual charge unknown | none | none authoritative | Card USD 0 | all zero, one incomplete marker |
| Complete that purchase | Card USD 0; actual charge now USD 673 | Card −673 once | Food USD 673 | Card USD −673 | USD 0 / 673 / −673 |
| Incomplete transfer | Bank USD 10000; Cash JPY 0; sent USD 1000, received unknown | none on either account | none | unchanged | all zero |
| Split expense | Bank USD 10000 | Bank −1500 | Food USD 1000; Household USD 500 | Bank USD 8500 | USD 0 / 1500 / −1500 |
| Parent/child split | Bank USD 10000 | Bank −1500 | Food USD 500; Food/Groceries USD 1000 | Bank USD 8500 | Food rollup 1500; USD 0 / 1500 / −1500 |
| Expense credit (+200) | Bank USD 0 | Bank +200 | Food USD 200 | Bank USD 200 | USD 0 / −200 / +200 |
| Different-account partial credit | Card USD −1500; Bank USD 0 | Bank +400 | Food USD 400 | Card −1500; Bank 400 | USD 0 / −400 / +400 in credit period |
| Second partial credit | Card USD −1500; Bank USD 400 | Bank +100 | Food USD 100 | Card −1500; Bank 500 | USD 0 / −100 / +100 in second credit period |
| Three-decimal expense | Cash KWD 10000 | Cash −1234 | Food KWD 1234 | Cash KWD 8766 | KWD 0 / 1234 / −1234 |

A contextual reference can point to multiple posted transactions of any kind.
It never supplies defaults or changes postings, allocations, reports or the
transaction's sign. References are preserved while editing, remain through trash,
and detach if their target is permanently purged. Copies and templates omit them.

Foreign receipt example: merchant JPY lines [500, 300, 200] sum to JPY 1000.
Actual USD 673 charge gives exact proportions [336.5, 201.9, 134.6] minor units.
Floors [336, 201, 134] leave two units; descending remainders assign them to the
second and third lines: [336, 202, 135], sum 673. Equal remainders use original
line ordering. User corrections must still sum to 673. Merchant [500, 300, 199]
against total 1000 fails reconciliation; no tax or adjustment is invented.

## Manual transaction state transitions

- New or copied editor → in-memory input: no database row, posting, balance,
  report, or recovery payload is written. A surviving ViewModel retains active
  form input; process termination loses it.
- Posted → in-memory edit: the original remains effective. Direct Save validates
  the current input and starting posted-record token, then replaces postings,
  allocations, items, tags and references in one database transaction. A stale
  token or failed write leaves the original untouched and the form available.
- New input with a genuinely missing actual amount → explicit incomplete: after
  validating every supplied value, the user confirms Save transaction. The record
  is stored with a concurrency token and has no postings or report contribution.
  Editing an incomplete record is an in-memory session; leaving it preserves the
  saved version. Delete checks its current token.
- Incomplete → posted: one transaction validates the complete input, checks the
  incomplete token and any posted-record token, replaces/creates the aggregate,
  verifies affected balances and consumes the incomplete row. Repeating a stable
  submission cannot post twice.
- Future dates are rejected on Save, including incomplete saves. Posted entries
  cannot be saved incomplete. Unknown transfer sides leave the whole transfer
  non-authoritative.
- Posted → trash and trash → posted retain their current token-checked atomic
  behavior. Trash → purge requires confirmation, removes dependents and detaches
  inbound references. Ordinary editor Back discards only its in-memory changes;
  a changed form asks for confirmation first.
- Any failed mutation rolls back the database. A failed editor save retains input
  and keeps the original posted aggregate effective.

Editing the USD 1234 expense above to signed amount −1500 replaces Cash −1234
with −1500 and Food 1234 with 1500 atomically: balance 8500, spending 1500. Trash returns
balance 10000/spending 0, restore returns 8500/1500, purge after trash keeps
10000/0. A stale edit or failed write leaves all these totals unchanged.
