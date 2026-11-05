package net.extrawdw.apps.miuisucks.powerkeeper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardWorkTest {
    @Test
    fun noneIsEmptyAndLeavesOtherWorkUnchanged() {
        val work = GuardWork.settings("aurogon")

        assertTrue(GuardWork.NONE.isEmpty)
        assertEquals(work, GuardWork.NONE + work)
    }

    @Test
    fun scopeKeepsTheWidestPass() {
        val merged = GuardWork.settings("millet") + GuardWork.full("boot") + GuardWork.settings("aurogon")

        assertEquals(GuardWork.Scope.FULL, merged.scope)
    }

    @Test
    fun packageEventsAccumulateBelowAFullPass() {
        val merged = GuardWork.packageChanged("a.app") + GuardWork.packageRestarted("b.app") +
            GuardWork.packageChanged("c.app")

        assertFalse(merged.isEmpty)
        assertEquals(setOf("a.app", "c.app"), merged.changedPackages)
        assertEquals(setOf("b.app"), merged.restartedPackages)
        assertEquals(GuardWork.Scope.NONE, merged.scope)
    }

    @Test
    fun fullPassSubsumesPackageEvents() {
        val merged = GuardWork.packageChanged("a.app") + GuardWork.full("shizuku-binder") +
            GuardWork.packageRestarted("b.app")

        assertTrue(merged.changedPackages.isEmpty())
        assertTrue(merged.restartedPackages.isEmpty())
        assertEquals(GuardWork.Scope.FULL, merged.scope)
    }

    @Test
    fun triggerNamesEveryEventSorted() {
        val merged = GuardWork.settings("user-present") + GuardWork.settings("aurogon")

        assertEquals("event:aurogon+user-present", merged.trigger)
    }
}
