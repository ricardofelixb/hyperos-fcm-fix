package net.extrawdw.apps.miuisucks.powerkeeper

/** Parses output returned by PackageManagerService's shell command entry point. */
internal object PackageManagerOutput {
    fun selectedStoppedPackages(output: String, selectedPackages: Collection<String>): List<String> {
        val selected = selectedPackages.toSet()
        return output.lineSequence()
            .map { it.trim().split(WHITESPACE) }
            .filter { fields -> STOPPED_FIELD in fields }
            .mapNotNull { fields ->
                fields.firstNotNullOfOrNull { field ->
                    field.removePrefix(PACKAGE_PREFIX).takeIf { it != field && it in selected }
                }
            }
            .distinct()
            .sorted()
            .toList()
    }

    private const val PACKAGE_PREFIX = "package:"
    private const val STOPPED_FIELD = "stopped=true"
    private val WHITESPACE = Regex("\\s+")
}
