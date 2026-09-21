package moe.frost.kochenite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import moe.frost.kochenite.domain.SampleRecord
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storage = (application as KocheniteApplication).storage
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == FoundationViewModel::class.java)
                return FoundationViewModel(storage) {
                    SampleRecord(UUID.randomUUID().toString(), 12345, "JPY", 0, System.currentTimeMillis())
                } as T
            }
        })[FoundationViewModel::class.java]
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            FoundationTheme { FoundationScreen(state, model::addSample, model::retry) }
        }
    }
}
