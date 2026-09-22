package moe.frost.kochenite

import android.app.Application
import moe.frost.kochenite.persistence.FoundationStorage

class KocheniteApplication : Application() {
    val financialStorage by lazy {
        moe.frost.kochenite.persistence.ledger
            .FinancialStorage(this)
    }
    val storage by lazy { FoundationStorage(this) }
}
