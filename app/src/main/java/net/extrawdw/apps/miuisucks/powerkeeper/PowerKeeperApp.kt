package net.extrawdw.apps.miuisucks.powerkeeper

import android.app.Application

class PowerKeeperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        PrivilegedServiceClient.init(this)
        // Shizuku starts this process when its binder arrives, so every process start re-establishes the guard.
        GuardService.start(this)
        AppLog.i(
            "App",
            "onCreate version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) pid=${android.os.Process.myPid()}",
        )
    }
}
