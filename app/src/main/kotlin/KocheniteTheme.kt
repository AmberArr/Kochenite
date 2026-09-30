package moe.frost.kochenite

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Colour roles mirror the "M3 Color" variable collection of the Figma redesign (teal seed #006A6A).
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF006A6A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF9CF1F0),
        onPrimaryContainer = Color(0xFF004F4F),
        secondary = Color(0xFF4A6363),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8E7),
        onSecondaryContainer = Color(0xFF324B4B),
        tertiary = Color(0xFF4B607C),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD3E4FF),
        onTertiaryContainer = Color(0xFF334863),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFF4FBFA),
        onBackground = Color(0xFF161D1D),
        surface = Color(0xFFF4FBFA),
        onSurface = Color(0xFF161D1D),
        surfaceVariant = Color(0xFFDAE5E4),
        onSurfaceVariant = Color(0xFF3F4948),
        surfaceTint = Color(0xFF006A6A),
        surfaceDim = Color(0xFFD5DBDB),
        surfaceBright = Color(0xFFF4FBFA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEFF5F4),
        surfaceContainer = Color(0xFFE9EFEE),
        surfaceContainerHigh = Color(0xFFE3E9E9),
        surfaceContainerHighest = Color(0xFFDDE4E3),
        outline = Color(0xFF6F7979),
        outlineVariant = Color(0xFFBEC9C8),
        inverseSurface = Color(0xFF2B3231),
        inverseOnSurface = Color(0xFFECF2F1),
        inversePrimary = Color(0xFF80D5D4),
        scrim = Color(0xFF000000),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF80D5D4),
        onPrimary = Color(0xFF003737),
        primaryContainer = Color(0xFF004F4F),
        onPrimaryContainer = Color(0xFF9CF1F0),
        secondary = Color(0xFFB0CCCB),
        onSecondary = Color(0xFF1B3534),
        secondaryContainer = Color(0xFF324B4B),
        onSecondaryContainer = Color(0xFFCCE8E7),
        tertiary = Color(0xFFB3C8E8),
        onTertiary = Color(0xFF1C314B),
        tertiaryContainer = Color(0xFF334863),
        onTertiaryContainer = Color(0xFFD3E4FF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0E1514),
        onBackground = Color(0xFFDDE4E3),
        surface = Color(0xFF0E1514),
        onSurface = Color(0xFFDDE4E3),
        surfaceVariant = Color(0xFF3F4948),
        onSurfaceVariant = Color(0xFFBEC9C8),
        surfaceTint = Color(0xFF80D5D4),
        surfaceDim = Color(0xFF0E1514),
        surfaceBright = Color(0xFF343A3A),
        surfaceContainerLowest = Color(0xFF090F0F),
        surfaceContainerLow = Color(0xFF161D1D),
        surfaceContainer = Color(0xFF1A2121),
        surfaceContainerHigh = Color(0xFF252B2B),
        surfaceContainerHighest = Color(0xFF2F3636),
        outline = Color(0xFF889392),
        outlineVariant = Color(0xFF3F4948),
        inverseSurface = Color(0xFFDDE4E3),
        inverseOnSurface = Color(0xFF2B3231),
        inversePrimary = Color(0xFF006A6A),
        scrim = Color(0xFF000000),
    )

/** Finance and chart roles that Material's colour scheme does not define. */
@Immutable
internal data class FinanceColors(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val caution: Color,
    val onCaution: Color,
    val cautionContainer: Color,
    val onCautionContainer: Color,
    val chart: List<Color>,
)

private val LightFinance =
    FinanceColors(
        income = Color(0xFF2E6A44),
        onIncome = Color(0xFFFFFFFF),
        incomeContainer = Color(0xFFB1F1C1),
        onIncomeContainer = Color(0xFF135232),
        caution = Color(0xFF7B5800),
        onCaution = Color(0xFFFFFFFF),
        cautionContainer = Color(0xFFFFDEA6),
        onCautionContainer = Color(0xFF5D4200),
        chart =
            listOf(
                Color(0xFF006A6A),
                Color(0xFF4B607C),
                Color(0xFF8B5000),
                Color(0xFF7D5260),
                Color(0xFF2E6A44),
                Color(0xFF6750A4),
            ),
    )

private val DarkFinance =
    FinanceColors(
        income = Color(0xFF96D5A6),
        onIncome = Color(0xFF00391C),
        incomeContainer = Color(0xFF135232),
        onIncomeContainer = Color(0xFFB1F1C1),
        caution = Color(0xFFF8BD48),
        onCaution = Color(0xFF412D00),
        cautionContainer = Color(0xFF5D4200),
        onCautionContainer = Color(0xFFFFDEA6),
        chart =
            listOf(
                Color(0xFF80D5D4),
                Color(0xFFB3C8E8),
                Color(0xFFFFB86E),
                Color(0xFFEFB8C8),
                Color(0xFF96D5A6),
                Color(0xFFD0BCFF),
            ),
    )

internal val LocalFinanceColors = staticCompositionLocalOf { LightFinance }

@Composable
internal fun KocheniteTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFinanceColors provides if (dark) DarkFinance else LightFinance) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content,
        )
    }
}
