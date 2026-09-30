package moe.frost.kochenite.ui

import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.ui.components.SignDisplay
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.invalidAmount
import moe.frost.kochenite.ui.components.symbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FormattingTest {
    private val jpy = BookCurrency("JPY", 0)
    private val usd = BookCurrency("USD", 2)
    private val kwd = BookCurrency("KWD", 3)

    @Test fun amountsShowSymbolsGroupingAndTheCurrencyScale() {
        assertEquals("¥1,284,560", jpy.display(1284560, locale = Locale.US))
        assertEquals("$2,410.55", usd.display(241055, locale = Locale.US))
        assertEquals("$0.05", usd.display(5, locale = Locale.US))
        assertEquals("¥0", jpy.display(0, SignDisplay.ALWAYS, Locale.US))
        assertTrue(kwd.display(1234, locale = Locale.US).endsWith("1.234"))
    }

    @Test fun signsFollowTheRequestedDisplay() {
        assertEquals("−¥42,180", jpy.display(-42180, locale = Locale.US))
        assertEquals("¥42,180", jpy.display(42180, locale = Locale.US))
        assertEquals("+$16.50", usd.display(1650, SignDisplay.ALWAYS, Locale.US))
        assertEquals("−$16.50", usd.display(-1650, SignDisplay.ALWAYS, Locale.US))
        assertEquals("$16.50", usd.display(-1650, SignDisplay.NONE, Locale.US))
    }

    @Test fun extremeAmountsStayExact() {
        assertEquals("−$92,233,720,368,547,758.08", usd.display(Long.MIN_VALUE, locale = Locale.US))
        assertEquals("¥9,223,372,036,854,775,807", jpy.display(Long.MAX_VALUE, locale = Locale.US))
    }

    @Test fun separatorsFollowTheLocaleAndCodesStandInForMissingSymbols() {
        assertTrue(usd.display(123450, locale = Locale.GERMANY).endsWith("1.234,50"))
        val chf = BookCurrency("CHF", 2)
        assertEquals("CHF ", chf.symbol(Locale.US))
        assertEquals("CHF 1,234.50", chf.display(123450, locale = Locale.US))
    }

    @Test fun amountTextIsUnsignedAndExact() {
        assertFalse(invalidAmount("", usd))
        assertFalse(invalidAmount("12.34", usd))
        assertFalse(invalidAmount("0", usd))
        assertTrue(invalidAmount("-5", usd))
        assertTrue(invalidAmount("1.234", usd))
        assertTrue(invalidAmount("1.5", jpy))
        assertTrue(invalidAmount("12", null))
        assertTrue(invalidAmount("abc", usd))
    }
}
