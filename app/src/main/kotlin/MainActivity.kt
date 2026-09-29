package moe.frost.kochenite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.frost.kochenite.domain.ledger.map
import moe.frost.kochenite.navigation.BookScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storage = (application as KocheniteApplication).financialStorage
        val model =
            ViewModelProvider(
                this,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        require(modelClass == BookViewModel::class.java)
                        return BookViewModel { storage.open().map { it.services } } as T
                    }
                },
            )[BookViewModel::class.java]
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            KocheniteTheme { BookScreen(state, model) }
        }
    }
}
