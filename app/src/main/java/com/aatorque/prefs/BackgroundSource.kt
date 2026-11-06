package com.aatorque.prefs

/**
 * A parsed representation of a stored background preference value.
 *
 * Kept free of Android types so it can be unit tested on the JVM.
 */
sealed interface BackgroundSource {
    data object Inherit : BackgroundSource
    data class Drawable(val name: String) : BackgroundSource
    data class Stored(val name: String) : BackgroundSource
}

fun parseBackground(value: String?): BackgroundSource {
    if (value == null) return BackgroundSource.Inherit
    val separator = value.indexOf(':')
    if (separator < 0) return BackgroundSource.Inherit
    val prefix = value.substring(0, separator)
    val name = value.substring(separator + 1)
    if (name.isEmpty()) return BackgroundSource.Inherit
    return when (prefix) {
        "resource" -> BackgroundSource.Drawable(name)
        "file" -> BackgroundSource.Stored(name)
        else -> BackgroundSource.Inherit
    }
}

fun encodeDrawable(name: String): String = "resource:$name"

fun encodeFile(name: String): String = "file:$name"
