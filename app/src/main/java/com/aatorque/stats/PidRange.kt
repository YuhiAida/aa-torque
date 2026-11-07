package com.aatorque.stats

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The initial gauge range for a PID, taken from one row of Torque's `getPIDInformation`
 * response. The csv layout is
 * `<longName>,<shortName>,<unit>,<maxValue>,<minValue>,<scale>`, so index 3 is the maximum
 * and index 4 is the minimum. The maximum is rounded up and the minimum down, so the gauge
 * always covers the full range Torque reports.
 *
 * A row that reports 0 for both bounds gives 0..100, so a new gauge is still usable.
 * Kept free of Android and AIDL types so it can be unit tested. See issue #157.
 */
fun pidRange(row: List<String>): Pair<Int, Int> {
    val max = ceil(row[3].toDouble()).toInt()
    val min = floor(row[4].toDouble()).toInt()
    return if (min == 0 && max == 0) 0 to 100 else min to max
}
