package com.aatorque.prefs

import com.aatorque.datastore.Operation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A rule is deleted on one tap, so undo has to put it back where it was. See issue #169.
 */
class AlarmUndoTest {

    @Test
    fun `a restored rule keeps its position`() {
        val model = StateModel()
        model.uiState.addAll(
            listOf(
                FormData(Operation.GT, 1.0),
                FormData(Operation.GT, 2.0),
                FormData(Operation.GT, 3.0)
            )
        )

        val removed = model.uiState[1]
        val position = model.uiState.indexOf(removed)
        model.delete(removed)
        assertEquals(listOf(1.0, 3.0), model.uiState.map { it.value })

        model.restore(position, removed)
        assertEquals(listOf(1.0, 2.0, 3.0), model.uiState.map { it.value })
    }

    @Test
    fun `restoring past the end appends instead of throwing`() {
        val model = StateModel()
        model.uiState.add(FormData(Operation.GT, 1.0))

        model.restore(99, FormData(Operation.GT, 5.0))

        assertEquals(listOf(1.0, 5.0), model.uiState.map { it.value })
    }
}
