package com.aatorque.stats

import java.util.regex.Pattern

/**
 * Normalises a stored icon value into the form used by ImageListPreference's
 * entryValues: a bare drawable name such as "ic_cylinder". Torque (and older
 * preference data) may store a full resource path like
 * "res/drawable/ic_cylinder.png"; map that to its name. A value that is
 * already a bare name (or empty) is returned unchanged.
 *
 * Kept free of Android types so it can be unit tested.
 */
private val ICON_PATH_PATTERN: Pattern =
    Pattern.compile("res/drawable/(?<name>.+)\\.[a-z]+")

fun iconEntryValue(icon: String?): String {
    if (icon.isNullOrBlank()) return icon.orEmpty()
    val matcher = ICON_PATH_PATTERN.matcher(icon)
    return if (matcher.matches()) matcher.group("name") ?: icon else icon
}
