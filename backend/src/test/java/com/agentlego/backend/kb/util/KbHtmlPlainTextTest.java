package com.agentlego.backend.kb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbHtmlPlainTextTest {

    @Test
    void toPlain_stripsTags() {
        assertEquals("a b", KbHtmlPlainText.toPlain("<p>a</p> <span>b</span>"));
    }

    @Test
    void toPlain_emptyHtml() {
        assertTrue(KbHtmlPlainText.toPlain("<p><br></p>").isEmpty());
    }
}
