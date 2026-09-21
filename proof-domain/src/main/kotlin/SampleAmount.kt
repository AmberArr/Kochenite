package moe.frost.kochenite.proof

import java.math.BigDecimal

fun minorUnits(value: String, scale: Int): Long {
    require(scale in 0..3)
    return BigDecimal(value).movePointRight(scale).longValueExact()
}
