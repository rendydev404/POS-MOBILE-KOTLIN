package com.sukashawarma.pos.presentation.login

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AutoLoginDeadlineTest {

    @Test
    fun `returns the result when auto-login finishes within one second`() = runTest {
        val result = runAutoLoginWithinDeadline { "session-restored" }

        assertEquals("session-restored", result)
    }

    @Test
    fun `cancels a slow auto-login after one second`() = runTest {
        try {
            runAutoLoginWithinDeadline {
                delay(AUTO_LOGIN_TIMEOUT_MS + 1)
                "late-result"
            }
            fail("Expected the auto-login deadline to cancel the request")
        } catch (_: TimeoutCancellationException) {
            // Expected: a delayed network operation cannot keep the login screen blocked.
        }
    }
}
