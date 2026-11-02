package com.aatorque.stats

/**
 * Parses a dashboard count from the settings dialog.
 *
 * Returns the integer value when it is in the allowed range (1..10), or null
 * otherwise (including empty / non-numeric input). Kept free of Android types
 * so it can be unit tested.
 */
fun screensCountOrNull(raw: String?): Int? {
    val value = raw?.toIntOrNull() ?: return null
    return if (value in 1..10) value else null
}
