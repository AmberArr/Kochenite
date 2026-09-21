package moe.frost.kochenite.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SampleTest {
    @Test fun exactMinorUnits() {
        assertEquals(123L, minorUnits("123", 0))
        assertEquals(123L, minorUnits("1.23", 2))
        assertEquals(123L, minorUnits("0.123", 3))
        assertEquals(9007199254740993L, minorUnits("90071992547409.93", 2))
        assertFailsWith<ArithmeticException> { minorUnits("1.001", 2) }
        assertFailsWith<ArithmeticException> { minorUnits("9223372036854775808", 0) }
        assertFailsWith<IllegalArgumentException> { minorUnits("1", -1) }
    }

    @Test fun validatesSyntheticRecords() {
        val record = SampleRecord("sample-id", 12345, "JPY", 0, 1700000000000)
        assertFailsWith<IllegalArgumentException> { record.copy(id = " ") }
        assertFailsWith<IllegalArgumentException> { record.copy(amountMinor = 0) }
        assertFailsWith<IllegalArgumentException> { record.copy(currency = "usd") }
        assertFailsWith<IllegalArgumentException> { record.copy(currencyScale = 4) }
        assertFailsWith<IllegalArgumentException> { record.copy(occurredAtEpochMillis = -1) }
        assertFailsWith<IllegalArgumentException> { record.copy(note = "x".repeat(501)) }
        assertEquals(null, record.note)
    }
}
