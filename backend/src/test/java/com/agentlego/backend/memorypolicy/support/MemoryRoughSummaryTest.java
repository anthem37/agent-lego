package com.agentlego.backend.memorypolicy.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRoughSummaryTest {

    @Test
    void summarize_short_unchanged() {
        assertEquals("hello", MemoryRoughSummary.summarize("hello"));
    }

    @Test
    void summarize_long_addsEllipsis() {
        String longText = "a".repeat(600);
        String s = MemoryRoughSummary.summarize(longText, 100);
        assertTrue(s.endsWith("…"));
        assertTrue(s.length() <= 102);
    }

    @Test
    void lastRoughBoundary_findsNewline() {
        String chunk = "a".repeat(40) + "\n" + "b".repeat(60);
        int b = MemoryRoughSummary.lastRoughBoundary(chunk);
        assertEquals(40, b);
    }

    @Test
    void resolveMaxChars_null_usesDefault() {
        assertEquals(MemoryRoughSummary.DEFAULT_MAX_CHARS, MemoryRoughSummary.resolveMaxChars(null));
    }

    @Test
    void resolveMaxChars_clamps() {
        assertEquals(16, MemoryRoughSummary.resolveMaxChars(1));
        assertEquals(8192, MemoryRoughSummary.resolveMaxChars(999_999));
        assertEquals(200, MemoryRoughSummary.resolveMaxChars(200));
    }
}
