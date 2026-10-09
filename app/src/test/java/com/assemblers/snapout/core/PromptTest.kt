package com.assemblers.snapout.core

import com.assemblers.snapout.ai.FallbackTemplates
import com.assemblers.snapout.ai.PromptBuilder
import com.assemblers.snapout.ai.ReframeContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTest {
    private val ctx = ReframeContext("01:12", "TikTok", 45, 120, true, "be asleep by midnight", 1, Strictness.BALANCED, listOf("Old \"quoted\" msg"))

    @Test fun contextJsonContainsFacts() {
        val j = ctx.toJson()
        assertTrue(j.contains("\"app\":\"TikTok\""))
        assertTrue(j.contains("\"minutes\":45"))
        assertTrue(j.contains("Old 'quoted' msg"))
    }

    @Test fun postFilterRejectsShaming() {
        assertNull(PromptBuilder.postFilter("You are such an addict."))
        assertNull(PromptBuilder.postFilter("   "))
        assertEquals("Hello there?", PromptBuilder.postFilter("  \"Hello   there?\" "))
    }

    @Test fun toneEscalates() {
        assertEquals("gentle and curious", ctx.tone())
        assertTrue(ctx.copy(interventionsTonight = 3).tone().startsWith("direct"))
    }

    @Test fun fallbackFillsPlaceholders() {
        val m = FallbackTemplates.pick(ctx)
        assertFalse(m.contains("{"))
    }
}
