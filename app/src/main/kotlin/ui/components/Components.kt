package moe.frost.kochenite.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.LocalFinanceColors
import moe.frost.kochenite.R

/**
 * The font size of [style] with the user's font scale limited to [maxScale], for labels in fixed-width
 * controls where a larger size could only be clipped.
 */
@Composable
internal fun cappedFontSize(
    style: TextStyle,
    maxScale: Float,
): TextUnit {
    val scale = LocalDensity.current.fontScale
    return if (scale > maxScale) style.fontSize * (maxScale / scale) else style.fontSize
}

/** Monetary columns align digit by digit. */
internal val TextStyle.tabular get() = copy(fontFeatureSettings = "tnum")

/**
 * Keeps a short label on one line at large font scales by shrinking it instead of clipping or breaking a
 * word. The label never grows past [style], and its floor ignores the font scale so it can still fit.
 */
@Composable
internal fun labelAutoSize(style: TextStyle): TextAutoSize =
    TextAutoSize.StepBased(minFontSize = with(LocalDensity.current) { 8.dp.toSp() }, maxFontSize = style.fontSize)

/** Content never stretches wider than this on expanded windows. */
internal val ContentMaxWidth = 640.dp

/** True inside the detail pane of a list-detail layout, whose surface is one step above the window's. */
internal val LocalInPane = compositionLocalOf { false }

@Composable
internal fun screenColor(): Color =
    if (LocalInPane.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface

@Composable
internal fun cardColor(): Color =
    if (LocalInPane.current) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow

@Composable
internal fun AppIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(painterResource(id), contentDescription, modifier, tint)
}

@Composable
internal fun IconAction(
    @DrawableRes id: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) { AppIcon(id, contentDescription, tint = tint) }
}

internal enum class Tone { SECONDARY, PRIMARY, TERTIARY, INCOME, CAUTION, NEUTRAL, ERROR }

@Composable
internal fun Tone.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val finance = LocalFinanceColors.current
    return when (this) {
        Tone.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        Tone.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        Tone.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Tone.INCOME -> finance.incomeContainer to finance.onIncomeContainer
        Tone.CAUTION -> finance.cautionContainer to finance.onCautionContainer
        Tone.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        Tone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
}

/** The tone always accompanies a distinct icon; colour alone never carries the meaning. */
@Composable
internal fun Avatar(
    @DrawableRes icon: Int,
    tone: Tone = Tone.SECONDARY,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
) {
    val (container, content) = tone.colors()
    Box(Modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        AppIcon(icon, null, Modifier.size(iconSize), tint = content)
    }
}

internal enum class NoticeTone { INFO, CAUTION, ERROR }

@Composable
internal fun Notice(
    message: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.INFO,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val finance = LocalFinanceColors.current
    val (container, content, icon) =
        when (tone) {
            NoticeTone.INFO -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, R.drawable.ic_info)
            NoticeTone.CAUTION -> Triple(finance.cautionContainer, finance.onCautionContainer, R.drawable.ic_warning)
            NoticeTone.ERROR -> Triple(scheme.errorContainer, scheme.onErrorContainer, R.drawable.ic_error_outline)
        }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .then(if (action != null && onAction != null) Modifier.clickable(role = Role.Button, onClick = onAction) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { if (tone == NoticeTone.ERROR) liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(icon, null, Modifier.size(20.dp), tint = content)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = content)
            if (action != null) Text(action, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(cardColor())
            .padding(vertical = 4.dp),
        content = content,
    )
}

/** The whole row is the touch target. */
@Composable
internal fun ListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    headlineColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    startPadding: Dp = 16.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(start = startPadding, end = 16.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge, color = headlineColor)
            if (!supporting.isNullOrBlank()) Text(supporting, style = MaterialTheme.typography.bodyMedium, color = supportingColor)
        }
        trailing?.invoke(this)
    }
}

@Composable
internal fun SwitchRow(
    headline: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked,
            onCheckedChange = null,
            enabled = enabled,
            thumbContent = if (checked) ({ AppIcon(R.drawable.ic_check, null, Modifier.size(16.dp)) }) else null,
        )
    }
}

@Composable
internal fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: String? = null,
    @DrawableRes actionIcon: Int? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(icon, Tone.NEUTRAL, size = 64.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            FilledTonalButton(onClick = onAction) {
                if (actionIcon != null) {
                    AppIcon(actionIcon, null, Modifier.padding(end = 8.dp).size(18.dp))
                }
                Text(action)
            }
        }
    }
}

/** Reconciliation status is text and icon, never colour alone. */
@Composable
internal fun StatusBadge(
    text: String,
    ok: Boolean,
) {
    val container = if (ok) LocalFinanceColors.current.incomeContainer else MaterialTheme.colorScheme.error
    val content = if (ok) LocalFinanceColors.current.onIncomeContainer else MaterialTheme.colorScheme.onError
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(container).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(if (ok) R.drawable.ic_check else R.drawable.ic_error_outline, null, Modifier.size(16.dp), tint = content)
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    close: Boolean = false,
    backEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            if (onBack != null) {
                IconAction(
                    if (close) R.drawable.ic_close else R.drawable.ic_arrow_back,
                    stringResource(if (close) R.string.close else R.string.back),
                    enabled = backEnabled,
                    onClick = onBack,
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = screenColor()),
    )
}

/** Medium flexible bar for top-level destinations: actions above a headline that collapses on scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val style =
        lerp(
            MaterialTheme.typography.headlineMedium,
            MaterialTheme.typography.titleLarge,
            scrollBehavior.state.collapsedFraction,
        )
    MediumTopAppBar(
        title = { Text(title, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }) },
        actions = actions,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = screenColor(),
                scrolledContainerColor = screenColor(),
            ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * A screen with its own app bar. The content column is padded for system bars and the keyboard.
 * [bottomBar] stays above the keyboard and carries its surface down behind the navigation bar.
 */
@Composable
internal fun AppScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    maxWidth: Dp = Dp.Unspecified,
    bottomBar: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val insets = WindowInsets.safeDrawing
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        snackbarHost = snackbarHost,
        containerColor = screenColor(),
        contentWindowInsets = if (bottomBar == null) insets else insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            // The width cap must precede fillMaxSize, which would otherwise pin the width to the parent's.
            Column((if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier).fillMaxSize()) {
                Column(Modifier.weight(1f), content = content)
                if (bottomBar != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            content = bottomBar,
                        )
                    }
                }
            }
        }
    }
}
