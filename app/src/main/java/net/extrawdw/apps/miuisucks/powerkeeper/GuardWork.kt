package net.extrawdw.apps.miuisucks.powerkeeper

/**
 * What the guard still owes, merged from every event seen since it last ran. A [Scope.FULL] pass
 * covers everything a narrower one would, so the package sets only matter below it.
 */
data class GuardWork(
    val scope: Scope = Scope.NONE,
    /** Packages installed or updated; new FCM apps among them are adopted. */
    val changedPackages: Set<String> = emptySet(),
    /** Packages that were force-stopped or restarted. */
    val restartedPackages: Set<String> = emptySet(),
    val triggers: Set<String> = emptySet(),
) {
    enum class Scope {
        NONE,

        /** Re-read the settings HyperOS rewrites and repair them. */
        SETTINGS,

        /** Everything, including the Greezer commands that only a system_server restart resets. */
        FULL,
    }

    val isEmpty: Boolean
        get() = scope == Scope.NONE && changedPackages.isEmpty() && restartedPackages.isEmpty()

    operator fun plus(other: GuardWork): GuardWork {
        val merged = maxOf(scope, other.scope)
        return GuardWork(
            scope = merged,
            changedPackages = if (merged == Scope.FULL) emptySet() else changedPackages + other.changedPackages,
            restartedPackages = if (merged == Scope.FULL) emptySet() else restartedPackages + other.restartedPackages,
            triggers = triggers + other.triggers,
        )
    }

    val trigger: String
        get() = "event:" + triggers.sorted().joinToString("+")

    companion object {
        val NONE = GuardWork()

        fun full(trigger: String) = GuardWork(Scope.FULL, triggers = setOf(trigger))

        fun settings(trigger: String) = GuardWork(Scope.SETTINGS, triggers = setOf(trigger))

        fun packageChanged(packageName: String) =
            GuardWork(changedPackages = setOf(packageName), triggers = setOf("package-changed"))

        fun packageRestarted(packageName: String) =
            GuardWork(restartedPackages = setOf(packageName), triggers = setOf("package-restarted"))
    }
}
