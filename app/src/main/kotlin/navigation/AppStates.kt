package moe.frost.kochenite.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.R
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.Avatar
import moe.frost.kochenite.ui.components.ContentMaxWidth
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.Tone

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier.widthIn(max = ContentMaxWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

/** No navigation is offered until storage is ready. */
@Composable
internal fun LoadingScreen() {
    CenteredState {
        CircularProgressIndicator(Modifier.size(48.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun StorageUnavailableScreen(retry: () -> Unit) {
    CenteredState {
        Avatar(R.drawable.ic_warning, Tone.CAUTION, size = 72.dp)
        Text(
            stringResource(R.string.unavailable),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.unavailable_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = retry) {
            AppIcon(R.drawable.ic_refresh, null, Modifier.padding(end = 8.dp).size(18.dp))
            Text(stringResource(R.string.retry))
        }
    }
}

/** The unreadable file is preserved; nothing here attempts a salvage. */
@Composable
internal fun RecoveryScreen(retry: () -> Unit) {
    CenteredState {
        Avatar(R.drawable.ic_error_outline, Tone.ERROR, size = 72.dp)
        Text(
            stringResource(R.string.recovery_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.recovery_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Notice(stringResource(R.string.recovery_notice))
        OutlinedButton(onClick = retry) {
            AppIcon(R.drawable.ic_refresh, null, Modifier.padding(end = 8.dp).size(18.dp))
            Text(stringResource(R.string.try_again))
        }
    }
}
