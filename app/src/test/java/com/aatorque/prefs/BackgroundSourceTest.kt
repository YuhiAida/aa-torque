package com.aatorque.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundSourceTest {
    @Test
    fun emptyReturnsInherit() = assertEquals(BackgroundSource.Inherit, parseBackground(""))

    @Test
    fun nullReturnsInherit() = assertEquals(BackgroundSource.Inherit, parseBackground(null))

    @Test
    fun resourceParses() = assertEquals(
        BackgroundSource.Drawable("background_incar_vw"),
        parseBackground("resource:background_incar_vw")
    )

    @Test
    fun fileParses() = assertEquals(
        BackgroundSource.Stored("bg-1a2b.jpg"),
        parseBackground("file:bg-1a2b.jpg")
    )

    @Test
    fun resourceNameMissingReturnsInherit() =
        assertEquals(BackgroundSource.Inherit, parseBackground("resource:"))

    @Test
    fun unknownPrefixReturnsInherit() =
        assertEquals(BackgroundSource.Inherit, parseBackground("blah:x"))

    @Test
    fun missingSeparatorReturnsInherit() =
        assertEquals(BackgroundSource.Inherit, parseBackground("blah"))

    @Test
    fun leadingSpaceReturnsInherit() =
        assertEquals(BackgroundSource.Inherit, parseBackground(" resource:foo"))

    @Test
    fun encodeDrawableProducesResourcePrefix() = assertEquals("resource:foo", encodeDrawable("foo"))

    @Test
    fun encodeFileProducesFilePrefix() = assertEquals("file:foo", encodeFile("foo"))
}
