package moe.frost.kochenite.transactions.list

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.ui.components.Avatar
import moe.frost.kochenite.ui.components.Tone
import moe.frost.kochenite.ui.components.tabular

/**
 * Transaction row: an icon and a grid. Row 1 is the title, an optional merchant or other-side amount
 * and the account amount. Row 2 is the category, the account and its running balance. Row 3 is a note.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LedgerRow(
    @DrawableRes icon: Int,
    tone: Tone,
    title: String,
    amount: String,
    amountColor: Color,
    category: String,
    account: String,
    modifier: Modifier = Modifier,
    balance: String? = null,
    otherAmount: String? = null,
    note: String? = null,
    selected: Boolean = false,
    dimmed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (selected) Modifier.clip(MaterialTheme.shapes.large).background(scheme.secondaryContainer) else Modifier)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .alpha(if (dimmed) 0.6f else 1f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(icon, tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    Modifier.weight(1f).alignByBaseline(),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (otherAmount != null) {
                    Text(
                        otherAmount,
                        Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.bodyMedium.tabular,
                        color = scheme.tertiary,
                        maxLines = 1,
                    )
                }
                Text(
                    amount,
                    Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    color = amountColor,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    category,
                    Modifier.weight(1f).alignByBaseline(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    account,
                    Modifier.weight(1f, fill = false).alignByBaseline(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (balance != null) {
                    Text(
                        balance,
                        Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.labelLarge.tabular,
                        color = scheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
            if (!note.isNullOrBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
