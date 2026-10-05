package mx.sisetracker.data.net

import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PoliteRequestQueueTest {
    private fun TestScope.queue() = PoliteRequestQueue(minIntervalMillis = 2_000) { testScheduler.currentTime }

    @Test
    fun `the next request starts 2 s after the previous one ended`() = runTest {
        val queue = queue()
        val starts = mutableListOf<Long>()

        queue.run {
            starts += currentTime
            delay(500)
        }
        queue.run { starts += currentTime }

        assertEquals(listOf(0L, 2_500L), starts)
    }

    @Test
    fun `concurrent requests run one at a time`() = runTest {
        val queue = queue()
        val starts = mutableListOf<Long>()
        var active = 0
        var maxActive = 0

        List(3) {
            launch {
                queue.run {
                    active++
                    maxActive = maxOf(maxActive, active)
                    starts += currentTime
                    delay(100)
                    active--
                }
            }
        }.joinAll()

        assertEquals(1, maxActive)
        assertEquals(listOf(0L, 2_100L, 4_200L), starts)
    }

    @Test
    fun `a failed request still spaces the next one`() = runTest {
        val queue = queue()

        runCatching { queue.run { throw IOException("offline") } }
        val start = queue.run { currentTime }

        assertEquals(2_000L, start)
    }

    @Test
    fun `no wait when the last request ended long ago`() = runTest {
        val queue = queue()

        queue.run {}
        advanceTimeBy(5_000)
        val start = queue.run { currentTime }

        assertEquals(5_000L, start)
    }
}
