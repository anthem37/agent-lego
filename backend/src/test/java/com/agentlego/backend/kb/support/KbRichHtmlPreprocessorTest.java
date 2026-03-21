package com.agentlego.backend.kb.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KbRichHtmlPreprocessorTest {

    @Test
    void expand_toolAndPlaceholderBecomeTokens() {
        String html =
                "<p>前<span class=\"kb-tool-mention\" data-kb-tool=\"order_q\">x</span>后"
                        + "<span class=\"kb-ph-embed\" data-kb-placeholder=\"orderNo\">y</span>尾</p>";
        String out = KbRichHtmlPreprocessor.expandKbMarksToPlainTokens(html);
        assertThat(out).contains("{{tool:order_q}}");
        assertThat(out).contains("{{orderNo}}");
        assertThat(out).doesNotContain("kb-tool-mention");
        assertThat(out).doesNotContain("kb-ph-embed");
    }

    @Test
    void convert_fullPipeline_preservesTokensInMarkdown() {
        String html = "<p>说明</p><p><span class=\"kb-tool-mention\" data-kb-tool=\"t1\"></span></p>";
        String md = KbHtmlToMarkdown.convert(html);
        assertThat(md).contains("{{tool:t1}}");
    }
}
