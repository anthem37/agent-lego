package com.agentlego.backend.kb.chunk;

import com.agentlego.backend.kb.util.KbMarkdownPlainText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbChunkSplitterTest {

    @Test
    void fixed_splitsLongText() {
        String s = "a".repeat(250);
        List<String> p = KbChunkSplitter.split(KbChunkStrategies.FIXED, s, s, "markdown", 100, 10);
        assertTrue(p.size() >= 2);
        assertTrue(p.stream().allMatch(x -> x.length() <= 100));
    }

    @Test
    void paragraph_mergesShortParagraphs() {
        String plain = "first para\n\nsecond para";
        List<String> p = KbChunkSplitter.split(KbChunkStrategies.PARAGRAPH, plain, plain, "markdown", 80, 0);
        assertEquals(1, p.size());
        assertTrue(p.get(0).contains("first"));
        assertTrue(p.get(0).contains("second"));
    }

    @Test
    void markdown_sections_splitsByHeadings() {
        String md = "# Section A\n\nalpha\n\n# Section B\n\nbeta";
        String plain = KbMarkdownPlainText.toPlain(md);
        List<String> p = KbChunkSplitter.split(
                KbChunkStrategies.MARKDOWN_SECTIONS,
                plain,
                md,
                "markdown",
                500,
                0
        );
        assertTrue(p.size() >= 2);
    }

    @Test
    void markdown_sections_onHtml_fallsBackToHybrid() {
        String plain = "x".repeat(300);
        String html = "<p>" + plain + "</p>";
        List<String> p = KbChunkSplitter.split(
                KbChunkStrategies.MARKDOWN_SECTIONS,
                plain,
                html,
                "html",
                120,
                20
        );
        assertTrue(p.size() >= 2);
    }
}
