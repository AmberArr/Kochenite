package moe.frost.kochenite

import android.app.Application

class KocheniteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.start(this)
    }

    val financialStorage by lazy {
        moe.frost.kochenite.persistence.ledger
            .FinancialStorage(this)
    }
}
