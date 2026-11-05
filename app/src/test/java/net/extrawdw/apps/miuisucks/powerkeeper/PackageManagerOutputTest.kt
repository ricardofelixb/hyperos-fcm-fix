package net.extrawdw.apps.miuisucks.powerkeeper

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageManagerOutputTest {
    @Test
    fun returnsOnlySelectedStoppedPackagesInStableOrder() {
        val output = """
            package:com.example.two uid:10202 stopped=true
            package:com.example.running uid:10203 stopped=false
            package:com.example.one uid:10201 stopped=true
            package:com.example.unselected uid:10204 stopped=true
            package:com.example.one uid:10201 stopped=true
        """.trimIndent()

        assertEquals(
            listOf("com.example.one", "com.example.two"),
            PackageManagerOutput.selectedStoppedPackages(
                output,
                listOf("com.example.two", "com.example.one", "com.example.running"),
            ),
        )
    }

    @Test
    fun emptyPackageListProducesNoStoppedPackages() {
        assertEquals(
            emptyList<String>(),
            PackageManagerOutput.selectedStoppedPackages("", listOf("com.example.push")),
        )
    }
}
