package com.agentlego.backend.kb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbMarkdownPlainTextTest {

    @Test
    void toPlain_headingAndList() {
        String md = "# Title\n\n- a\n- b";
        String plain = KbMarkdownPlainText.toPlain(md);
        assertTrue(plain.contains("Title"));
        assertTrue(plain.contains("a"));
        assertTrue(plain.contains("b"));
    }

    @Test
    void toPlain_inline() {
        assertEquals("bold", KbMarkdownPlainText.toPlain("**bold**").replaceAll("\\s+", "").toLowerCase());
    }
}
