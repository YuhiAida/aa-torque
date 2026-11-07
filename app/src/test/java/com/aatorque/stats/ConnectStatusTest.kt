package com.aatorque.stats

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The dashboard subscribes to the connection state after the bind attempt. A state set
 * before that subscription must still be visible, or the car sits on "Connecting to
 * Torque…" forever when Torque Pro is missing. See issue #160.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ConnectStatusTest {

    @Test
    fun `a missing torque is visible to a late subscriber`() = runBlocking {
        val refresher = TorqueRefresher()
        refresher.reportTorqueMissing()

        // A bounded wait: if the state becomes an event stream again, this fails in a
        // second instead of hanging the test run.
        assertEquals(ConnectStatus.NO_TORQUE, withTimeout(1000) { refresher.connectStatus.first() })
    }

    @Test
    fun `the state starts as connecting`() = runBlocking {
        assertEquals(
            ConnectStatus.CONNECTING_TORQUE,
            withTimeout(1000) { TorqueRefresher().connectStatus.first() }
        )
    }
}
