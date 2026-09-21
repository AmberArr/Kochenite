package moe.frost.kochenite

import android.app.Application
import moe.frost.kochenite.persistence.FoundationStorage

class KocheniteApplication : Application() {
    val storage by lazy { FoundationStorage(this) }
}
