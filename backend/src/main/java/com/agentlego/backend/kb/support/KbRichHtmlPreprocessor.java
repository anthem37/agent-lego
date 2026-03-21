package com.agentlego.backend.kb.support;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

/**
 * 将知识库富文本中的「内嵌节点」展开为与正文校验/占位符/工具引用一致的纯文本标记，
 * 再交给 HTML→Markdown 转换器。
 * <p>
 * 与前端 Quill 约定：
 * <ul>
 *     <li>{@code <span class="kb-tool-mention" data-kb-tool="…">} → {@code {{tool:…}}}</li>
 *     <li>{@code <span class="kb-ph-embed" data-kb-placeholder="…">} → {@code {{…}}}</li>
 * </ul>
 */
public final class KbRichHtmlPreprocessor {

    private KbRichHtmlPreprocessor() {
    }

    public static String expandKbMarksToPlainTokens(String html) {
        if (html == null || html.isBlank()) {
            return html == null ? "" : html;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        Element body = doc.body();
        for (Element el : body.select("span.kb-tool-mention[data-kb-tool]")) {
            String token = el.attr("data-kb-tool").trim();
            if (!token.isEmpty()) {
                el.replaceWith(new TextNode("{{tool:" + token + "}}"));
            }
        }
        for (Element el : body.select("span.kb-ph-embed[data-kb-placeholder]")) {
            String key = el.attr("data-kb-placeholder").trim();
            if (!key.isEmpty()) {
                el.replaceWith(new TextNode("{{" + key + "}}"));
            }
        }
        return body.html();
    }
}
