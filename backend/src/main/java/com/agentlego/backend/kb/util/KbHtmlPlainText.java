package com.agentlego.backend.kb.util;

import org.jsoup.Jsoup;

/**
 * 将富文本 HTML 转为纯文本，供分片与 ilike 检索。
 */
public final class KbHtmlPlainText {

    private KbHtmlPlainText() {
    }

    public static String toPlain(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String t = Jsoup.parse(html).text();
        return t == null ? "" : t.strip();
    }
}
