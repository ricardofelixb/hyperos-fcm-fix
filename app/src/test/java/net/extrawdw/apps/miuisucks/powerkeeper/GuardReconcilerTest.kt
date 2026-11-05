package net.extrawdw.apps.miuisucks.powerkeeper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardReconcilerTest {
    @Test
    fun aManagedPackageChangeReconcilesAurogonEvenWhenAnotherCallerAdoptedItFirst() {
        assertTrue(GuardReconciler.reconcilesFcmSettings(GuardWork.Scope.NONE, managedChanges = listOf("a.app")))
    }

    @Test
    fun aSettingsChangeAlwaysReconcilesAurogon() {
        assertTrue(GuardReconciler.reconcilesFcmSettings(GuardWork.Scope.SETTINGS, managedChanges = emptyList()))
    }

    @Test
    fun aRestartOrAnUnmanagedChangeLeavesAurogonAlone() {
        assertFalse(GuardReconciler.reconcilesFcmSettings(GuardWork.Scope.NONE, managedChanges = emptyList()))
    }
}
