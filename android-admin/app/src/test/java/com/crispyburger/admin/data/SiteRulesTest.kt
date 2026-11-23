package com.crispyburger.admin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteRulesTest {
    private val base = SiteRules.DEFAULT_CONFIG

    @Test fun defaultConfigIsPublishable() = assertEquals(emptyList<String>(), SiteRules.problems(base))

    @Test fun everyPresetIsReadable() {
        SiteRules.PRESETS.forEach { p ->
            assertEquals(p.name, emptyList<String>(), SiteRules.problems(base.copy(theme = p.theme)))
        }
    }

    @Test fun unreadableColoursAreBlocked() {
        val bad = base.copy(theme = base.theme.copy(ink = "#FFFFFF"))
        assertTrue(SiteRules.problems(bad).isNotEmpty())
    }

    @Test fun menuCanNeverBeHidden() {
        val hidden = SiteRules.setVisible(base, "menu", false)
        assertTrue(hidden.sections.first { it.id == "menu" }.visible)
    }

    @Test fun moveSwapsNeighboursAndStopsAtEdges() {
        val down = SiteRules.move(base, "hero", 1)
        assertEquals("menu", down.sections[0].id)
        assertEquals("hero", down.sections[1].id)
        assertEquals(base, SiteRules.move(base, "hero", -1))
    }

    @Test fun customBoxesCanBeAddedAndRemovedButBuiltInsCannot() {
        val added = SiteRules.addCustom(base, SectionType.TEXT, "abc123")
        assertEquals(base.sections.size + 1, added.sections.size)
        assertEquals(base.sections.size, SiteRules.removeCustom(added, "c_abc123").sections.size)
        assertEquals(base, SiteRules.removeCustom(base, "menu"))
    }

    @Test fun emptyCustomBoxesAskForContent() {
        val withButton = SiteRules.addCustom(base, SectionType.BUTTON, "btn")
        assertTrue(SiteRules.problems(withButton).any { it.contains("label") })
    }

    @Test fun linksMustBeSafe() {
        assertTrue(SiteRules.isValidLink("/orders"))
        assertTrue(SiteRules.isValidLink("https://example.com/menu"))
        assertFalse(SiteRules.isValidLink("javascript:alert(1)"))
        assertFalse(SiteRules.isValidLink("//evil.example"))
        assertFalse(SiteRules.isValidLink("http://insecure.example"))
        assertFalse(SiteRules.isValidLink("/has space"))
    }

    @Test fun hexIsNormalised() {
        assertEquals("#FFC93C", SiteRules.normalizeHex("ffc93c"))
        assertNull(SiteRules.normalizeHex("#12"))
    }

    @Test fun roundTripsThroughJson() {
        val json = SiteJson.encodeToString(SiteConfig.serializer(), base)
        assertEquals(base, SiteJson.decodeFromString(SiteConfig.serializer(), json))
    }
}
