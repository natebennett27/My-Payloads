package com.trio.today.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brand mapping behind the reliability wizard.
 *
 * Worth testing because a typo here means an affected user silently gets no
 * guidance at all, and the symptom -- reminders that never arrive -- looks
 * nothing like the cause.
 */
class OemGuidanceTableTest {

    @Test
    fun `covers the manufacturers the report names`() {
        listOf("samsung", "Xiaomi", "OnePlus", "OPPO", "realme", "vivo", "HUAWEI")
            .forEach { brand ->
                assertNotNull("expected guidance for $brand", OemGuidanceTable.forManufacturer(brand))
            }
    }

    @Test
    fun `matches manufacturer names case-insensitively`() {
        assertEquals(
            OemGuidanceTable.forManufacturer("samsung"),
            OemGuidanceTable.forManufacturer("SAMSUNG"),
        )
    }

    @Test
    fun `sub-brands map to their parent guidance`() {
        assertEquals("Xiaomi", OemGuidanceTable.forManufacturer("Redmi")?.brandLabel)
        assertEquals("Xiaomi", OemGuidanceTable.forManufacturer("POCO")?.brandLabel)
        assertEquals("Huawei", OemGuidanceTable.forManufacturer("honor")?.brandLabel)
        assertEquals("vivo", OemGuidanceTable.forManufacturer("iqoo")?.brandLabel)
    }

    @Test
    fun `realme keeps its own label while sharing OPPO's steps`() {
        val realme = OemGuidanceTable.forManufacturer("realme")!!
        val oppo = OemGuidanceTable.forManufacturer("oppo")!!
        assertEquals("realme", realme.brandLabel)
        assertEquals("OPPO", oppo.brandLabel)
        assertEquals(oppo.steps, realme.steps)
    }

    @Test
    fun `every entry carries usable steps and at least one target`() {
        listOf("samsung", "xiaomi", "oneplus", "oppo", "vivo", "huawei").forEach { brand ->
            val guidance = OemGuidanceTable.forManufacturer(brand)!!
            assertTrue("$brand has no steps", guidance.steps.isNotEmpty())
            assertTrue("$brand has no targets", guidance.settingsTargets.isNotEmpty())
            assertTrue("$brand has a blank step", guidance.steps.none { it.isBlank() })
            assertTrue(
                "$brand has a malformed target",
                guidance.settingsTargets.all { (pkg, cls) -> pkg.isNotBlank() && cls.contains('.') },
            )
        }
    }

    @Test
    fun `unknown manufacturers get no extra steps`() {
        assertNull(OemGuidanceTable.forManufacturer("Google"))
        assertNull(OemGuidanceTable.forManufacturer(""))
    }
}
