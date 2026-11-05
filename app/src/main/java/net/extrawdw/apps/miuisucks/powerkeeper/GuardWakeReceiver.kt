package net.extrawdw.apps.miuisucks.powerkeeper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GuardWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i("Receiver", "received action=${intent.action}")
        if (intent.action !in SUPPORTED_ACTIONS) {
            AppLog.w("Receiver", "ignored unsupported action=${intent.action}")
            return
        }
        // Boot and app update are the only moments nothing of ours is running; the service takes it from here.
        GuardService.start(context.applicationContext)
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
