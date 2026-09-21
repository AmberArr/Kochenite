package moe.frost.kochenite.proof.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.frost.kochenite.proof.storage.ProofRecord
import moe.frost.kochenite.proof.storage.openProofDatabase
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { ProofScreen() }
    }
}

@Composable
private fun ProofScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<ProofRecord>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    suspend fun refresh(add: Boolean) {
        busy = true
        failed = false
        try {
            records = withContext(Dispatchers.IO) {
                val db = openProofDatabase(context)
                try {
                    if (add) db.samples().insert(
                        ProofRecord(UUID.randomUUID().toString(), 12345L, "JPY", 0, System.currentTimeMillis()),
                    )
                    db.samples().all()
                } finally {
                    db.close()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh(false) }
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF80D5C5), surface = Color(0xFF171A19))
    } else {
        lightColorScheme(primary = Color(0xFF006B5E), surface = Color(0xFFFAFCFB))
    }
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.samples), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.record_count, records.size), Modifier.testTag("record-count"))
                Button(
                    onClick = { scope.launch { refresh(true) } },
                    enabled = !busy && !failed,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.add_sample)) }
                when {
                    busy -> Text(stringResource(R.string.loading))
                    failed -> {
                        Text(stringResource(R.string.storage_error))
                        TextButton(onClick = { scope.launch { refresh(false) } }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                    else -> LazyColumn(Modifier.testTag("ready"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (records.isEmpty()) item { Text(stringResource(R.string.empty)) }
                        itemsIndexed(records, key = { _, record -> record.id }) { index, record ->
                            val formatted = remember(record) {
                                NumberFormat.getCurrencyInstance().apply {
                                    currency = Currency.getInstance(record.currency)
                                    minimumFractionDigits = record.currencyScale
                                    maximumFractionDigits = record.currencyScale
                                }.format(BigDecimal.valueOf(record.amountMinor, record.currencyScale))
                            }
                            Column(Modifier.testTag("sample-row")) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.sample, index + 1), Modifier.weight(1f))
                                    Text(formatted, style = MaterialTheme.typography.titleMedium)
                                }
                                HorizontalDivider(Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
