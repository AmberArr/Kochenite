package moe.frost.kochenite

import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerResult

internal fun failure(result: LedgerResult<*>): Int =
    when (result) {
        LedgerResult.Conflict -> {
            R.string.conflict_message
        }

        LedgerResult.TemporarilyUnavailable -> {
            R.string.unavailable
        }

        LedgerResult.UnexpectedFailure -> {
            R.string.unexpected_failure
        }

        LedgerResult.RecoveryRequired -> {
            R.string.recovery
        }

        is LedgerResult.Invalid -> {
            when (result.reason) {
                moe.frost.kochenite.domain.ledger.InvalidReason.CURRENCY -> R.string.currency_locked_error
                moe.frost.kochenite.domain.ledger.InvalidReason.CATEGORY -> R.string.category_error
                moe.frost.kochenite.domain.ledger.InvalidReason.OVERFLOW -> R.string.overflow_error
                InvalidReason.FUTURE_DATE -> R.string.future_date_error
                InvalidReason.MISSING_AMOUNT -> R.string.required_amount_error
                InvalidReason.ALLOCATIONS -> R.string.items_error
                InvalidReason.AMOUNT -> R.string.amount_error
                else -> R.string.correct_fields
            }
        }

        else -> {
            R.string.correct_fields
        }
    }
