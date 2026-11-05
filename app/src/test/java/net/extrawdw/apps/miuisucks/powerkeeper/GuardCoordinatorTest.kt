package net.extrawdw.apps.miuisucks.powerkeeper

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardCoordinatorTest {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private val runs = LinkedBlockingQueue<GuardWork>()

    @After
    fun tearDown() = scope.cancel()

    private fun coordinator(succeed: () -> Boolean = { true }) =
        GuardCoordinator(scope, debounceMillis = 50, maxDelayMillis = 1_000) { work ->
            runs.add(work)
            succeed()
        }

    private fun nextRun(): GuardWork? = runs.poll(2, TimeUnit.SECONDS)

    @Test
    fun aBurstOfEventsRunsOnceWithTheMergedWork() {
        val coordinator = coordinator()

        coordinator.submit(GuardWork.packageChanged("a.app"))
        coordinator.submit(GuardWork.packageRestarted("b.app"))
        coordinator.submit(GuardWork.settings("aurogon"))

        val work = nextRun()!!
        assertEquals(setOf("a.app"), work.changedPackages)
        assertEquals(setOf("b.app"), work.restartedPackages)
        assertEquals(GuardWork.Scope.SETTINGS, work.scope)
        Thread.sleep(200)
        assertNull(runs.poll())
    }

    @Test
    fun nothingRunsWithoutAnEvent() {
        coordinator()

        Thread.sleep(200)
        assertNull(runs.poll())
    }

    @Test
    fun workThatCouldNotFinishWaitsForTheNextEventAndIsMergedWithIt() {
        val succeed = AtomicBoolean(false)
        val coordinator = coordinator { succeed.get() }

        coordinator.submit(GuardWork.full("boot"))
        assertEquals(GuardWork.Scope.FULL, nextRun()!!.scope)
        Thread.sleep(200)
        assertNull("a failed pass is not retried on a timer", runs.poll())

        succeed.set(true)
        coordinator.submit(GuardWork.packageChanged("a.app"))
        val retry = nextRun()!!
        assertEquals(GuardWork.Scope.FULL, retry.scope)
        assertEquals("event:boot+package-changed", retry.trigger)
    }
}
