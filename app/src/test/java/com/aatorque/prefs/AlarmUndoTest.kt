package com.aatorque.prefs

import com.aatorque.datastore.Operation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A rule is deleted on one tap, so undo has to put it back in its place. The list order is
 * the precedence: the first rule that matches supplies the colour. See issue #169.
 */
class AlarmUndoTest {

    @Test
    fun `a restored rule lands in its sorted place`() {
        val model = StateModel()
        model.prependItem(FormData(Operation.GT, 10.0))
        model.prependItem(FormData(Operation.GT, 20.0))
        model.prependItem(FormData(Operation.GT, 30.0))
        assertEquals(listOf(30.0, 20.0, 10.0), model.uiState.map { it.value })

        val removed = model.uiState.first { it.value == 20.0 }
        model.delete(removed)
        assertEquals(listOf(30.0, 10.0), model.uiState.map { it.value })

        model.restore(removed)
        assertEquals(listOf(30.0, 20.0, 10.0), model.uiState.map { it.value })
    }

    /**
     * The case a remembered index gets wrong: delete 20, delete 30, undo 30, undo 20. A
     * remembered index would drop 20 below 10 and hand the colour to the wrong rule.
     */
    @Test
    fun `undo keeps the order when another rule is deleted first`() {
        val model = StateModel()
        model.prependItem(FormData(Operation.GT, 10.0))
        model.prependItem(FormData(Operation.GT, 20.0))
        model.prependItem(FormData(Operation.GT, 30.0))

        val twenty = model.uiState.first { it.value == 20.0 }
        val thirty = model.uiState.first { it.value == 30.0 }
        model.delete(twenty)
        model.delete(thirty)
        assertEquals(listOf(10.0), model.uiState.map { it.value })

        model.restore(thirty)
        model.restore(twenty)
        assertEquals(listOf(30.0, 20.0, 10.0), model.uiState.map { it.value })
    }

    @Test
    fun `restoring a rule that is already there does nothing`() {
        val model = StateModel()
        model.prependItem(FormData(Operation.GT, 10.0))

        model.restore(model.uiState.first())

        assertEquals(listOf(10.0), model.uiState.map { it.value })
    }
}
