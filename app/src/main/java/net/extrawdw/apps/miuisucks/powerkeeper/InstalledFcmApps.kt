package net.extrawdw.apps.miuisucks.powerkeeper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

data class InstalledFcmApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val installed: Boolean,
)

/** Finds third-party FCM apps without requesting QUERY_ALL_PACKAGES. */
object FcmAppDiscovery {
    /** Non-system packages in this profile that declare an FCM receiver; [packageName] narrows the scan to one app. */
    @SuppressLint("QueryPermissionsNeeded")
    @Suppress("DEPRECATION")
    fun find(context: Context, packageName: String? = null): Set<String> {
        val pm = context.packageManager
        return pm.queryBroadcastReceivers(
            Intent(AurogonConfig.FCM_RECEIVE_ACTION).setPackage(packageName),
            PackageManager.MATCH_ALL,
        )
            .mapNotNull { it.activityInfo?.packageName }
            .filterTo(linkedSetOf()) { candidate ->
                candidate != context.packageName &&
                    candidate != ANDROID_SYSTEM_PACKAGE &&
                    runCatching { !pm.getApplicationInfo(candidate, 0).isSystemPackage }.getOrDefault(false)
            }
    }
}

object InstalledFcmApps {
    private const val ICON_SIZE_PX = 128

    fun load(context: Context, savedPackages: Collection<String>): List<InstalledFcmApp> {
        val pm = context.packageManager
        val discovered = FcmAppDiscovery.find(context).toMutableSet()
        discovered += savedPackages
        discovered -= context.packageName
        discovered -= ANDROID_SYSTEM_PACKAGE

        return discovered.mapNotNull { packageName ->
            runCatching {
                val info = pm.getApplicationInfo(packageName, 0)
                if (info.isSystemPackage) return@mapNotNull null
                InstalledFcmApp(
                    packageName = packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info).toImageBitmap(ICON_SIZE_PX) }.getOrNull(),
                    installed = true,
                )
            }.getOrElse {
                InstalledFcmApp(packageName, packageName, null, installed = false)
            }
        }
    }

    private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
        (this as? BitmapDrawable)?.bitmap?.let { return it.scale(sizePx, sizePx).asImageBitmap() }
        val bitmap = createBitmap(sizePx, sizePx)
        setBounds(0, 0, sizePx, sizePx)
        draw(Canvas(bitmap))
        return bitmap.asImageBitmap()
    }
}

private const val ANDROID_SYSTEM_PACKAGE = "android"

private val ApplicationInfo.isSystemPackage: Boolean
    get() = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
