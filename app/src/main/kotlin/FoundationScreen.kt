package moe.frost.kochenite

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.domain.SampleRecord
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

@Composable
internal fun FoundationTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors =
        if (dark) {
            darkColorScheme(
                primary = Color(0xFF80D5C5),
                onPrimary = Color(0xFF00382F),
                background = Color(0xFF171A19),
                onBackground = Color(0xFFE2E6E3),
                surface = Color(0xFF171A19),
                onSurface = Color(0xFFE2E6E3),
                onSurfaceVariant = Color(0xFFBDC9C3),
                outlineVariant = Color(0xFF414A45),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF006B5E),
                onPrimary = Color.White,
                background = Color(0xFFFAFCFB),
                onBackground = Color(0xFF202522),
                surface = Color(0xFFFAFCFB),
                onSurface = Color(0xFF202522),
                onSurfaceVariant = Color(0xFF49574F),
                outlineVariant = Color(0xFFC4CEC8),
            )
        }
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoundationScreen(
    state: FoundationState,
    add: () -> Unit,
    retry: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(insets),
            contentPadding =
                PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = insets.calculateTopPadding() + 16.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.samples),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            when (state) {
                FoundationState.Loading -> {
                    item {
                        CircularProgressIndicator(Modifier.size(32.dp))
                        Text(stringResource(R.string.loading))
                    }
                }

                FoundationState.TemporarilyUnavailable, FoundationState.RecoveryRequired -> {
                    item {
                        Text(
                            stringResource(if (state == FoundationState.RecoveryRequired) R.string.recovery else R.string.unavailable),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        TextButton(onClick = retry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
                    }
                }

                is FoundationState.Ready -> {
                    item {
                        Text(stringResource(R.string.record_count, state.records.size), Modifier.testTag("record-count"))
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = add,
                            enabled = !state.saving,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("add-sample"),
                        ) {
                            Text(stringResource(if (state.saving) R.string.saving else R.string.add_sample))
                        }
                        if (state.saveFailed) {
                            Text(
                                stringResource(R.string.save_failed),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                    if (state.records.isEmpty()) item { Text(stringResource(R.string.empty), Modifier.testTag("ready")) }
                    items(state.records, key = { it.id }) { record -> SampleRow(record) }
                }
            }
        }
    }
}

@Composable
private fun SampleRow(record: SampleRecord) {
    val amount =
        remember(record) {
            NumberFormat
                .getCurrencyInstance()
                .apply {
                    currency = Currency.getInstance(record.currency)
                    minimumFractionDigits = record.currencyScale
                    maximumFractionDigits = record.currencyScale
                }.format(BigDecimal.valueOf(record.amountMinor, record.currencyScale))
        }
    val date =
        remember(record) {
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(record.occurredAtEpochMillis))
        }
    Column(Modifier.fillMaxWidth().testTag("record-${record.id}").semantics(mergeDescendants = true) {}) {
        Text(amount, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.sample), style = MaterialTheme.typography.bodyLarge)
        Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        record.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        HorizontalDivider(Modifier.padding(top = 16.dp))
    }
}
