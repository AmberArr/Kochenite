package moe.frost.kochenite.proof

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SampleAmountTest {
    @Test
    fun exactAmounts() {
        assertEquals(123L, minorUnits("123", 0))
        assertEquals(123L, minorUnits("1.23", 2))
        assertEquals(123L, minorUnits("0.123", 3))
        assertEquals(9007199254740993L, minorUnits("90071992547409.93", 2))
    }

    @Test
    fun rejectsRoundingAndOverflow() {
        assertFailsWith<ArithmeticException> { minorUnits("1.001", 2) }
        assertFailsWith<ArithmeticException> { minorUnits("9223372036854775808", 0) }
        assertFailsWith<IllegalArgumentException> { minorUnits("1", -1) }
    }
}
