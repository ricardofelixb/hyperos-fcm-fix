package net.extrawdw.apps.miuisucks.powerkeeper

import org.junit.Assert.assertEquals
import org.junit.Test

class MilletNoRestrictListTest {
    private val required = listOf("com.google.android.gms", "net.extrawdw.apps.miuisucks.powerkeeper")

    @Test
    fun appendsRequiredPackagesAfterEveryExistingEntry() {
        val existing = "com.android.vending, net.extrawdw.apps.notisync"

        assertEquals(
            listOf(
                "com.android.vending",
                "net.extrawdw.apps.notisync",
                "com.google.android.gms",
                "net.extrawdw.apps.miuisucks.powerkeeper",
            ),
            MilletNoRestrictList.appendMissing(existing, required),
        )
    }

    @Test
    fun leavesExistingRequiredEntriesInPlace() {
        val existing = "com.google.android.gms, com.android.vending"

        assertEquals(
            listOf("com.google.android.gms", "com.android.vending", "net.extrawdw.apps.miuisucks.powerkeeper"),
            MilletNoRestrictList.appendMissing(existing, required),
        )
    }

    @Test
    fun treatsMissingSettingAsAnEmptyList() {
        assertEquals(
            required,
            MilletNoRestrictList.appendMissing("null", required),
        )
    }

    @Test
    fun trimsAndDeduplicatesWithoutReordering() {
        val existing = " com.android.vending,com.android.vending, net.extrawdw.apps.notisync "

        assertEquals(
            "com.android.vending, net.extrawdw.apps.notisync, com.google.android.gms, " +
                "net.extrawdw.apps.miuisucks.powerkeeper",
            MilletNoRestrictList.serialize(MilletNoRestrictList.appendMissing(existing, required)),
        )
    }
}
