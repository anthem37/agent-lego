package com.agentlego.backend.kb.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KbHtmlToMarkdownTest {

    @Test
    void convert_headingAndList() {
        String html = "<h1>标题</h1><p>段落</p><ul><li>a</li><li>b</li></ul>";
        String md = KbHtmlToMarkdown.convert(html);
        assertThat(md).contains("标题");
        assertThat(md).contains("段落");
        assertThat(md).contains("a");
        assertThat(md).contains("b");
    }

    @Test
    void convert_blankReturnsEmpty() {
        assertThat(KbHtmlToMarkdown.convert(null)).isEmpty();
        assertThat(KbHtmlToMarkdown.convert("   ")).isEmpty();
    }
}
