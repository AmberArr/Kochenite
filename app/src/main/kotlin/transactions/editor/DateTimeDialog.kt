package moe.frost.kochenite.transactions.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import moe.frost.kochenite.R
import moe.frost.kochenite.ui.components.Segmented
import moe.frost.kochenite.ui.components.clockTime
import moe.frost.kochenite.ui.components.weekdayDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Days after today cannot be chosen: scheduled transactions are not supported. */
@OptIn(ExperimentalMaterial3Api::class)
private object NotAfterToday : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !Instant
            .ofEpochMilli(utcTimeMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .isAfter(LocalDate.now())

    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}

/** Combined date and time dialog with Date and Time tabs and a 24-hour clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeDialog(
    date: LocalDate,
    time: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalTime) -> Unit,
) {
    var showTime by remember { mutableStateOf(false) }
    val dateState =
        rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = NotAfterToday,
        )
    val timeState = rememberTimePickerState(time.hour, time.minute, true)
    val chosenDate = dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: date
    val chosenTime = LocalTime.of(timeState.hour, timeState.minute)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            // The width cap must precede fillMaxWidth, which would otherwise pin the width to the window's.
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 400.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 8.dp)) {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.transaction_date_time),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text("${weekdayDate(chosenDate)} · ${clockTime(chosenTime)}", style = MaterialTheme.typography.headlineMedium)
                    Segmented(
                        listOf(false to stringResource(R.string.date), true to stringResource(R.string.time)),
                        showTime,
                        Modifier.padding(bottom = 8.dp).testTag("date-time-tabs"),
                    ) { showTime = it }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (showTime) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { TimePicker(timeState) }
                } else {
                    DatePicker(
                        dateState,
                        title = null,
                        headline = null,
                        showModeToggle = false,
                        colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        onClick = { onConfirm(chosenDate, chosenTime) },
                        modifier = Modifier.testTag("date-time-done"),
                    ) { Text(stringResource(R.string.done)) }
                }
            }
        }
    }
}
