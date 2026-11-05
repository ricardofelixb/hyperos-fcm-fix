package net.extrawdw.apps.miuisucks.powerkeeper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import rikka.shizuku.Shizuku

/**
 * Holds the observers and receivers that replace polling. It lives in the app process rather than
 * the Shizuku UserService: that process runs as shell without a Context or Looper, so content
 * observers and dynamic receivers would need hidden binder APIs. Package broadcasts are also exempt
 * from implicit-broadcast delivery to manifest receivers, so something has to be alive to hear them;
 * a foreground service is the one thing HyperOS reliably keeps running.
 */
class GuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var coordinator: GuardCoordinator
    private lateinit var store: GuardSettingsStore
    private var gameAllowlistObserver: ContentObserver? = null
    private val observers = mutableListOf<ContentObserver>()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        AppLog.i(LOG_TAG, "Shizuku binder available")
        coordinator.submit(GuardWork.full("shizuku-binder"))
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            AppLog.i(LOG_TAG, "Shizuku permission granted")
            coordinator.submit(GuardWork.full("shizuku-permission"))
        }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            if (packageName == context.packageName) return
            when (intent.action) {
                // An update sends ADDED (replacing) and then REPLACED; REPLACED alone covers it.
                Intent.ACTION_PACKAGE_ADDED ->
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        coordinator.submit(GuardWork.packageChanged(packageName))
                    }
                Intent.ACTION_PACKAGE_REPLACED -> coordinator.submit(GuardWork.packageChanged(packageName))
                Intent.ACTION_PACKAGE_RESTARTED -> coordinator.submit(GuardWork.packageRestarted(packageName))
            }
        }
    }
    private val userPresentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            coordinator.submit(GuardWork.settings("user-present"))
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = GuardSettingsStore(this)
        val reconciler = GuardReconciler(this)
        coordinator = GuardCoordinator(scope, DEBOUNCE_MILLIS, MAX_DELAY_MILLIS) { reconciler.run(it) }

        observe(Settings.System.getUriFor(MilletNoRestrictList.SETTING_NAME), "millet-no-restrict")
            .also(observers::add)
        observe(Settings.Global.getUriFor(AurogonConfig.SETTING_NAME), "aurogon").also(observers::add)
        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_RESTARTED)
                addDataScheme("package")
            },
            RECEIVER_NOT_EXPORTED,
        )
        registerReceiver(userPresentReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), RECEIVER_NOT_EXPORTED)
        // The sticky variant also fires right away when the binder is already here, which is what
        // makes a restarted service run a full pass.
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        AppLog.i(LOG_TAG, "created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        syncGameAllowlistObserver()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLog.i(LOG_TAG, "destroyed")
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        unregisterReceiver(packageReceiver)
        unregisterReceiver(userPresentReceiver)
        (observers + listOfNotNull(gameAllowlistObserver)).forEach(contentResolver::unregisterContentObserver)
        scope.cancel()
        super.onDestroy()
    }

    /** Watches the nighttime game allowlist only while the option that depends on it is on. */
    private fun syncGameAllowlistObserver() {
        val wanted = store.loadNighttimeFcmProtectionEnabled()
        if (wanted && gameAllowlistObserver == null) {
            gameAllowlistObserver = observe(Settings.System.getUriFor(GameAllowlist.SETTING_NAME), "game-allowlist")
        } else if (!wanted) {
            gameAllowlistObserver?.let(contentResolver::unregisterContentObserver)
            gameAllowlistObserver = null
        }
    }

    private fun observe(uri: Uri, trigger: String): ContentObserver {
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                coordinator.submit(GuardWork.settings(trigger))
            }
        }
        contentResolver.registerContentObserver(uri, false, observer)
        return observer
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.guard_notification_channel),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guard)
            .setContentTitle(getString(R.string.guard_notification_title))
            .setContentText(getString(R.string.guard_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    companion object {
        private const val LOG_TAG = "GuardService"
        private const val CHANNEL_ID = "guard"
        private const val NOTIFICATION_ID = 1
        private const val DEBOUNCE_MILLIS = 2_000L
        private const val MAX_DELAY_MILLIS = 20_000L

        /** Starts the service, or just refreshes its observers when it already runs. */
        fun start(context: Context) {
            if (!XiaomiCompatibility.check(context).supported) return
            runCatching { context.startForegroundService(Intent(context, GuardService::class.java)) }
                .onFailure { AppLog.w(LOG_TAG, "start refused: ${it.message}") }
        }
    }
}
