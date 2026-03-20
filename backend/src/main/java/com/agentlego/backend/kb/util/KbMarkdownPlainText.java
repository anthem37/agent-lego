package com.agentlego.backend.kb.util;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

/**
 * 将 Markdown 转为纯文本：先渲染为 HTML，再用 {@link KbHtmlPlainText} 去标签，
 * 与富文本分片逻辑一致，便于 ilike 检索。
 */
public final class KbMarkdownPlainText {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();

    private KbMarkdownPlainText() {
    }

    public static String toPlain(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = PARSER.parse(markdown.strip());
        String html = HTML_RENDERER.render(document);
        return KbHtmlPlainText.toPlain(html);
    }
}
