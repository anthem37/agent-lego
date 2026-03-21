package com.agentlego.backend.kb.support;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

/**
 * 将富文本 HTML 转为 Markdown，供知识库分块/向量化（与纯 Markdown 入库路径一致）。
 */
public final class KbHtmlToMarkdown {

    private static final FlexmarkHtmlConverter CONVERTER = FlexmarkHtmlConverter.builder().build();

    private KbHtmlToMarkdown() {
    }

    public static String convert(String html) {
        if (html == null) {
            return "";
        }
        String t = html.trim();
        if (t.isEmpty()) {
            return "";
        }
        try {
            String expanded = KbRichHtmlPreprocessor.expandKbMarksToPlainTokens(t);
            return CONVERTER.convert(expanded).trim();
        } catch (Exception e) {
            return "";
        }
    }
}
