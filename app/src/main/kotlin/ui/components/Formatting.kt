package moe.frost.kochenite.ui.components

import android.text.format.DateFormat
import moe.frost.kochenite.domain.ledger.BookCurrency
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

internal fun currency(code: String): BookCurrency? =
    try {
        Currency.getInstance(code.trim().uppercase()).let { BookCurrency(it.currencyCode, it.defaultFractionDigits) }
    } catch (_: IllegalArgumentException) {
        null
    }

internal enum class SignDisplay {
    /** "−¥640" and "¥640": balances and totals. */
    NEGATIVE,

    /** "−¥640" and "+¥640": flows whose direction matters. */
    ALWAYS,

    /** "¥640" for any sign: magnitudes whose sign is shown elsewhere. */
    NONE,
}

/** The locale's symbol, or the code followed by a narrow space when the locale has no symbol. */
internal fun BookCurrency.symbol(locale: Locale = Locale.getDefault()): String {
    val symbol = runCatching { Currency.getInstance(code).getSymbol(locale) }.getOrDefault(code)
    return if (symbol == code) "$code " else symbol
}

/** Display-only text such as "−¥1,284,560" or "+$16.50". Parsing and storage keep using exact plain decimals. */
internal fun BookCurrency.display(
    minor: Long,
    sign: SignDisplay = SignDisplay.NEGATIVE,
    locale: Locale = Locale.getDefault(),
): String {
    val magnitude = BigDecimal.valueOf(minor, scale).abs()
    val number =
        NumberFormat
            .getNumberInstance(locale)
            .apply {
                minimumFractionDigits = scale
                maximumFractionDigits = scale
                isGroupingUsed = true
            }.format(magnitude)
    val prefix =
        when {
            sign == SignDisplay.NONE -> ""
            minor < 0 -> "−"
            minor > 0 && sign == SignDisplay.ALWAYS -> "+"
            else -> ""
        }
    return prefix + symbol(locale) + number
}

private fun formatter(
    skeleton: String,
    locale: Locale = Locale.getDefault(),
): DateTimeFormatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** "Sep 29", with the year when it is not the current one. */
internal fun shortDate(
    date: LocalDate,
    today: LocalDate = LocalDate.now(),
): String = date.format(formatter(if (date.year == today.year) "MMMd" else "yMMMd"))

/** "Mon, Sep 29", with the year when it is not the current one. */
internal fun weekdayDate(
    date: LocalDate,
    today: LocalDate = LocalDate.now(),
): String = date.format(formatter(if (date.year == today.year) "EEEMMMd" else "yEEEMMMd"))

/** "Mon, Sep 29, 2026". */
internal fun fullDate(date: LocalDate): String = date.format(formatter("yEEEMMMd"))

/** "Sep 29, 2026". */
internal fun mediumDate(date: LocalDate): String = date.format(formatter("yMMMd"))

internal fun clockTime(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("HH:mm"))

/** "September 2026". */
internal fun monthTitle(month: YearMonth): String = month.atDay(1).format(formatter("yMMMM"))

/** "September". */
internal fun monthName(month: YearMonth): String = month.atDay(1).format(formatter("LLLL"))
