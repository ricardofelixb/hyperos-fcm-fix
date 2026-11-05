package net.extrawdw.apps.miuisucks.powerkeeper

enum class AppDozePolicy(
    val code: Int,
    val persistedValue: String,
    val titleRes: Int,
    val descriptionRes: Int,
) {
    UNRESTRICTED(
        code = 1,
        persistedValue = "unrestricted",
        titleRes = R.string.app_doze_unrestricted,
        descriptionRes = R.string.app_doze_unrestricted_description,
    ),
    DEFAULT(
        code = 2,
        persistedValue = "default",
        titleRes = R.string.app_doze_default,
        descriptionRes = R.string.app_doze_default_description,
    ),
    RESTRICTED(
        code = 3,
        persistedValue = "restricted",
        titleRes = R.string.app_doze_restricted,
        descriptionRes = R.string.app_doze_restricted_description,
    );

    companion object {
        fun fromCode(code: Int): AppDozePolicy = entries.firstOrNull { it.code == code } ?: DEFAULT

        fun fromPersistedValue(value: String?): AppDozePolicy =
            entries.firstOrNull { it.persistedValue == value } ?: DEFAULT
    }
}

data class AppPolicy(
    val packageName: String,
    val appEnabled: Boolean = false,
    val aurogonEnabled: Boolean = false,
    val autoUnstopEnabled: Boolean = false,
    val autostartManaged: Boolean = false,
    val autostartEnabled: Boolean = false,
    val dozeManaged: Boolean = false,
    val dozePolicy: AppDozePolicy = AppDozePolicy.DEFAULT,
) {
    fun withAppEnabled(enabled: Boolean, initializeDefaults: Boolean = false): AppPolicy =
        if (!enabled) {
            copy(appEnabled = false)
        } else if (initializeDefaults) {
            copy(
                appEnabled = true,
                aurogonEnabled = true,
                autoUnstopEnabled = true,
                autostartManaged = true,
                autostartEnabled = true,
            )
        } else {
            copy(appEnabled = true)
        }

    fun appOffCleanupPolicy(): AppPolicy? =
        if (dozeManaged) {
            AppPolicy(
                packageName = packageName,
                appEnabled = true,
                dozeManaged = true,
                dozePolicy = AppDozePolicy.DEFAULT,
            )
        } else {
            null
        }
}

object AppPolicyDefaults {
    /** An FCM app with no saved choice: protected from the start, with AOSP battery management left alone. */
    fun discovered(packageName: String): AppPolicy = AppPolicy(
        packageName = packageName,
        appEnabled = true,
        aurogonEnabled = true,
        autoUnstopEnabled = true,
        autostartManaged = true,
        autostartEnabled = true,
    )

    fun forPackage(packageName: String): AppPolicy = AppPolicy(packageName)
}
