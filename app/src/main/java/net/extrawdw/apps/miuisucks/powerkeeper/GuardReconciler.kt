package net.extrawdw.apps.miuisucks.powerkeeper

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Runs the privileged work a [GuardWork] asks for. Returns false when it should be retried on a later event. */
class GuardReconciler(context: Context) {
    private val context = context.applicationContext
    private val store = GuardSettingsStore(this.context)

    suspend fun run(work: GuardWork): Boolean {
        val compatibility = XiaomiCompatibility.check(context)
        if (!compatibility.supported) {
            AppLog.e(LOG_TAG, "unsupported: ${compatibility.reason}")
            return true
        }
        if (!shizukuReady()) {
            AppLog.w(LOG_TAG, "Shizuku not ready; keeping ${work.trigger} pending")
            return false
        }
        AppLog.i(LOG_TAG, "start scope=${work.scope} trigger=${work.trigger} changed=${work.changedPackages.size} restarted=${work.restartedPackages.size}")
        return try {
            val report = if (work.scope == GuardWork.Scope.FULL) fullPass(work) else eventPass(work)
            val failed = report.contains("FAILED") || report.contains("exit_code=")
            AppLog.i(LOG_TAG, "finish failed=$failed trigger=${work.trigger} report=$report")
            // Narrow passes run constantly and mostly find nothing to repair; keep the last-run card for the interesting ones.
            if (work.scope == GuardWork.Scope.FULL || failed) store.saveLastRun(!failed, report)
            !failed
        } catch (throwable: Throwable) {
            AppLog.e(LOG_TAG, "failed trigger=${work.trigger}", throwable)
            store.saveLastRun(false, "Guard pass failed: ${throwable.message ?: throwable.javaClass.simpleName}")
            false
        }
    }

    private suspend fun fullPass(work: GuardWork): String {
        store.adoptFcmApps(FcmAppDiscovery.find(context))
        val settings = store.loadSettings()
        val users = store.loadEnabledAndroidUserIds()
        val enforcement = PrivilegedServiceClient.enforce(
            settings.aurogonEnabledPackages,
            settings.enabledAppPolicies,
            users,
            settings.fcmReconnectEnabled,
            work.trigger,
        )
        return "$enforcement\n${unstop(settings.autoUnstopPackages, users, work.trigger)}"
    }

    private suspend fun eventPass(work: GuardWork): String {
        store.adoptFcmApps(work.changedPackages.flatMap { FcmAppDiscovery.find(context, it) })
        val settings = store.loadSettings()
        val users = store.loadEnabledAndroidUserIds()
        val managedChanges = work.changedPackages.filter { settings.policyFor(it).appEnabled }
        return buildList {
            managedChanges.forEach { packageName ->
                add(PrivilegedServiceClient.applyAppPolicy(settings.policyFor(packageName), users, work.trigger))
            }
            // A new rule is only effective once it is in the Aurogon setting. Whoever adopted the app first
            // (the UI saves defaults when it opens) did not write it, so this cannot depend on who adopted it.
            if (reconcilesFcmSettings(work.scope, managedChanges)) {
                add(
                    PrivilegedServiceClient.reconcileFcmSettings(
                        settings.aurogonEnabledPackages,
                        settings.fcmReconnectEnabled,
                        work.trigger,
                    ),
                )
            }
            val toUnstop = (managedChanges + work.restartedPackages).filter { it in settings.autoUnstopPackages }
            if (toUnstop.isNotEmpty()) add(unstop(toUnstop, users, work.trigger))
        }.joinToString("\n")
    }

    private suspend fun unstop(packageNames: Collection<String>, users: List<Int>, trigger: String): String =
        if (packageNames.isEmpty()) {
            "Auto unstop: no packages selected"
        } else {
            PrivilegedServiceClient.unstop(packageNames, users, trigger)
        }

    private fun shizukuReady(): Boolean = runCatching {
        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    companion object {
        private const val LOG_TAG = "Guard"

        fun reconcilesFcmSettings(scope: GuardWork.Scope, managedChanges: Collection<String>): Boolean =
            scope == GuardWork.Scope.SETTINGS || managedChanges.isNotEmpty()
    }
}
