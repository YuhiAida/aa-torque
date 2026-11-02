package com.aatorque.stats

import com.aatorque.datastore.DecimalPlaces
import com.aatorque.datastore.Display

// Resolves how many fraction digits should be rendered for a display.
// null means legacy variable behaviour (0..2 digits).
fun resolvedDecimalPlaces(display: Display): Int? = when (display.decimalPlaces) {
    DecimalPlaces.ZERO -> 0
    DecimalPlaces.ONE -> 1
    DecimalPlaces.TWO -> 2
    else -> if (display.wholeNumbers) 0 else null
}
