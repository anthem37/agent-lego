package com.agentlego.backend.kb.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 Markdown 一级/二级标题切节：content 为整节；embedding 用 路径 + 标题 + 引导段。
 */
public final class KbHeadingSectionSplitter {

    private static final Pattern H1_LINE = Pattern.compile("^#\\s+(.+?)\\s*$");
    private static final Pattern H2_LINE = Pattern.compile("^##\\s+(.+?)\\s*$");
    /**
     * 在「行首一级 # 」前切分（不匹配 ##）
     */
    private static final Pattern H1_SPLIT = Pattern.compile("(?m)(?=^#\\s+[^#])");
    private static final Pattern H2_SPLIT = Pattern.compile("(?m)(?=^##\\s+)");

    private KbHeadingSectionSplitter() {
    }

    public static List<KbChunkSlice> split(
            String body,
            int headingLevel,
            int leadMaxChars,
            int maxFallbackWindow,
            int overlap
    ) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        String t = body.trim();
        List<RawSection> raw = headingLevel <= 1 ? splitH1(t) : splitH2(t);
        if (raw.isEmpty()) {
            return fallback(t, maxFallbackWindow, overlap);
        }
        List<KbChunkSlice> out = new ArrayList<>();
        for (RawSection s : raw) {
            out.addAll(toSlices(s, leadMaxChars, maxFallbackWindow, overlap));
        }
        return out;
    }

    private static List<RawSection> splitH1(String body) {
        String[] parts = H1_SPLIT.split(body);
        List<RawSection> list = new ArrayList<>();
        if (parts.length == 0) {
            return List.of();
        }
        if (!parts[0].isBlank()) {
            String pre = parts[0].trim();
            list.add(new RawSection("文档", "前言", pre, pre));
        }
        for (int i = 1; i < parts.length; i++) {
            String block = parts[i].trim();
            if (block.isEmpty()) {
                continue;
            }
            String fl = block.lines().findFirst().orElse("");
            Matcher m = H1_LINE.matcher(fl);
            String title = m.matches() ? m.group(1).trim() : firstLineTitle(block);
            list.add(new RawSection(title, title, block, stripFirstLine(block)));
        }
        return list.stream().filter(s -> !s.fullText.isBlank()).toList();
    }

    private static List<RawSection> splitH2(String body) {
        String[] h1Parts = H1_SPLIT.split(body);
        List<RawSection> list = new ArrayList<>();
        int start = 0;
        if (h1Parts.length > 0 && !h1Parts[0].isBlank()) {
            String pre = h1Parts[0].trim();
            list.add(new RawSection("文档", "前言", pre, pre));
            start = 1;
        }
        for (int i = start; i < h1Parts.length; i++) {
            String block = h1Parts[i].trim();
            if (block.isEmpty()) {
                continue;
            }
            String fl = block.lines().findFirst().orElse("");
            Matcher m1 = H1_LINE.matcher(fl);
            String h1Title = m1.matches() ? m1.group(1).trim() : "文档";
            String afterH1 = stripFirstLine(block);
            if (afterH1.isBlank()) {
                list.add(new RawSection(h1Title, h1Title, block, ""));
                continue;
            }
            if (!afterH1.trim().startsWith("##")) {
                list.add(new RawSection(h1Title, h1Title, block, afterH1));
                continue;
            }
            String[] h2Parts = H2_SPLIT.split(afterH1);
            if (!h2Parts[0].isBlank()) {
                String intro = h2Parts[0].trim();
                list.add(new RawSection(h1Title, h1Title + " > 概述", fl + "\n" + intro, intro));
            }
            for (int j = 1; j < h2Parts.length; j++) {
                String b = h2Parts[j].trim();
                if (b.isEmpty()) {
                    continue;
                }
                String fl2 = b.lines().findFirst().orElse("");
                Matcher m2 = H2_LINE.matcher(fl2);
                String h2 = m2.matches() ? m2.group(1).trim() : firstLineTitle(b);
                String path = h1Title + " > " + h2;
                list.add(new RawSection(path, h2, b, stripFirstLine(b)));
            }
        }
        return list.stream().filter(s -> !s.fullText.isBlank()).toList();
    }

    private static List<KbChunkSlice> toSlices(RawSection s, int leadMaxChars, int maxWin, int overlap) {
        String full = s.fullText.trim();
        String leadSrc = s.bodyForLead.isBlank() ? full : s.bodyForLead;
        String lead = extractLead(leadSrc, leadMaxChars);
        String embBase = s.sectionPath + "\n" + s.titleLine + "\n\n" + lead;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sectionPath", s.sectionPath);
        meta.put("headingStrategy", "HEADING_SECTION");

        if (full.length() <= maxWin * 3) {
            return List.of(new KbChunkSlice(full, embBase, meta));
        }
        List<String> parts = KbTextChunker.chunk(full, maxWin, overlap);
        List<KbChunkSlice> slices = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            Map<String, Object> m = new LinkedHashMap<>(meta);
            m.put("partIndex", i);
            m.put("partTotal", parts.size());
            String subLead = extractLead(p, leadMaxChars);
            String emb = s.sectionPath + "\n" + s.titleLine + "\n\n" + subLead;
            slices.add(new KbChunkSlice(p, emb, m));
        }
        return slices;
    }

    private static String extractLead(String body, int leadMaxChars) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String b = body.trim();
        int n = Math.min(Math.max(leadMaxChars, 64), 8192);
        if (b.length() <= n) {
            return b;
        }
        return b.substring(0, n).trim() + "…";
    }

    private static List<KbChunkSlice> fallback(String body, int maxWin, int overlap) {
        List<String> parts = KbTextChunker.chunk(body, maxWin, overlap);
        List<KbChunkSlice> out = new ArrayList<>();
        for (String p : parts) {
            out.add(KbChunkSlice.uniform(p, KbChunkSlice.meta("headingStrategy", "HEADING_FALLBACK")));
        }
        return out;
    }

    private static String stripFirstLine(String block) {
        List<String> ls = new ArrayList<>(block.lines().toList());
        if (ls.isEmpty()) {
            return "";
        }
        ls.remove(0);
        return String.join("\n", ls).trim();
    }

    private static String firstLineTitle(String block) {
        String fl = block.lines().findFirst().orElse("").trim();
        if (fl.length() > 120) {
            return fl.substring(0, 120) + "…";
        }
        return fl;
    }

    private record RawSection(
            String sectionPath,
            String titleLine,
            String fullText,
            String bodyForLead
    ) {
    }
}
