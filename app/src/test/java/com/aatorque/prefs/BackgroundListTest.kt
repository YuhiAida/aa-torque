package com.aatorque.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundListTest {
    @Test
    fun buildRowsOrderAndTitles() {
        val bundled = listOf("Bundled A" to "drawable_a", "Bundled B" to "drawable_b")
        val stored = listOf("file1.jpg", "file2.jpg")
        val rows = buildRows(bundled, stored, "resource:drawable_b")
        assertEquals(7, rows.size)
        assertEquals(BackgroundRow.Kind.DEFAULT, rows[0].kind)
        assertEquals("App default", rows[0].title)
        assertEquals("", rows[0].value)
        assertEquals(BackgroundRow.Kind.DRAWABLE, rows[1].kind)
        assertEquals("Bundled A", rows[1].title)
        assertEquals("resource:drawable_a", rows[1].value)
        assertEquals(BackgroundRow.Kind.DRAWABLE, rows[2].kind)
        assertEquals("Bundled B", rows[2].title)
        assertEquals("resource:drawable_b", rows[2].value)
        assertEquals(BackgroundRow.Kind.DIVIDER, rows[3].kind)
        assertEquals(BackgroundRow.Kind.FILE, rows[4].kind)
        assertEquals("Custom image 1", rows[4].title)
        assertEquals("file:file1.jpg", rows[4].value)
        assertEquals(BackgroundRow.Kind.FILE, rows[5].kind)
        assertEquals("Custom image 2", rows[5].title)
        assertEquals("file:file2.jpg", rows[5].value)
        assertEquals(BackgroundRow.Kind.CHOOSE, rows[6].kind)
        assertEquals("Choose image…", rows[6].title)
    }
}
