package com.agentlego.backend.kb.chunk;

import com.agentlego.backend.kb.util.KbMarkdownPlainText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将纯文本（及可选的 Markdown 原文）切分为检索用片段。
 */
public final class KbChunkSplitter {

    private static final Pattern MD_HEADING = Pattern.compile("(?m)^(#{1,6})\\s+");

    private KbChunkSplitter() {
    }

    /**
     * @param strategy       策略名（已小写规范化）
     * @param plainForChunks 用于分片的全文纯文本
     * @param rawStored      入库原文（Markdown 节策略需要 MD 源码）
     * @param contentFormat  markdown | html
     * @param chunkSize      目标最大块长
     * @param overlap        滑窗重叠
     */
    public static List<String> split(
            String strategy,
            String plainForChunks,
            String rawStored,
            String contentFormat,
            int chunkSize,
            int overlap
    ) {
        if (plainForChunks == null || plainForChunks.isEmpty()) {
            return List.of();
        }
        return switch (strategy) {
            case KbChunkStrategies.FIXED -> splitFixed(plainForChunks, chunkSize, overlap);
            case KbChunkStrategies.PARAGRAPH -> splitParagraph(plainForChunks, chunkSize, overlap, false);
            case KbChunkStrategies.HYBRID -> splitParagraph(plainForChunks, chunkSize, overlap, true);
            case KbChunkStrategies.MARKDOWN_SECTIONS ->
                    splitMarkdownSections(plainForChunks, rawStored, contentFormat, chunkSize, overlap);
            default -> splitFixed(plainForChunks, chunkSize, overlap);
        };
    }

    private static List<String> splitMarkdownSections(
            String plainForChunks,
            String rawStored,
            String contentFormat,
            int chunkSize,
            int overlap
    ) {
        if (!"markdown".equals(contentFormat) || rawStored == null || rawStored.isBlank()) {
            return splitParagraph(plainForChunks, chunkSize, overlap, true);
        }
        List<String> sections = splitMarkdownByHeadings(rawStored.strip());
        if (sections.size() <= 1) {
            return splitParagraph(plainForChunks, chunkSize, overlap, true);
        }
        List<String> all = new ArrayList<>();
        for (String section : sections) {
            if (section == null || section.isBlank()) {
                continue;
            }
            String sectionPlain = KbMarkdownPlainText.toPlain(section);
            if (sectionPlain.isEmpty()) {
                continue;
            }
            all.addAll(splitParagraph(sectionPlain, chunkSize, overlap, true));
        }
        return all.isEmpty() ? splitParagraph(plainForChunks, chunkSize, overlap, true) : all;
    }

    private static List<String> splitMarkdownByHeadings(String md) {
        Matcher m = MD_HEADING.matcher(md);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
        }
        if (starts.isEmpty()) {
            return List.of(md);
        }
        List<String> out = new ArrayList<>();
        if (starts.get(0) > 0) {
            String pre = md.substring(0, starts.get(0)).strip();
            if (!pre.isEmpty()) {
                out.add(pre);
            }
        }
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : md.length();
            String sec = md.substring(from, to).strip();
            if (!sec.isEmpty()) {
                out.add(sec);
            }
        }
        return out;
    }

    /**
     * 按空行分段 → 合并至 chunkSize → 超长再切；softBreak 为 true 时用软边界滑窗。
     */
    private static List<String> splitParagraph(String plain, int chunkSize, int overlap, boolean softBreak) {
        String[] paras = plain.split("\\r?\\n\\s*\\r?\\n+");
        List<String> merged = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String raw : paras) {
            String p = raw.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() >= chunkSize) {
                flushMergeBuffer(merged, cur);
                merged.addAll(splitLarge(p, chunkSize, overlap, softBreak));
                continue;
            }
            int addLen = p.length() + (cur.isEmpty() ? 0 : 2);
            if (cur.length() + addLen <= chunkSize) {
                if (!cur.isEmpty()) {
                    cur.append("\n\n");
                }
                cur.append(p);
            } else {
                flushMergeBuffer(merged, cur);
                cur.append(p);
            }
        }
        flushMergeBuffer(merged, cur);
        return merged;
    }

    private static void flushMergeBuffer(List<String> merged, StringBuilder cur) {
        if (!cur.isEmpty()) {
            merged.add(cur.toString().strip());
            cur.setLength(0);
        }
    }

    private static List<String> splitLarge(String text, int chunkSize, int overlap, boolean softBreak) {
        if (softBreak) {
            return splitFixedSoft(text, chunkSize, overlap);
        }
        return splitFixed(text, chunkSize, overlap);
    }

    /**
     * 固定滑窗
     */
    public static List<String> splitFixed(String content, int chunkSize, int overlap) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        if (content.length() <= chunkSize) {
            return List.of(content);
        }
        int step = Math.max(1, chunkSize - overlap);
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkSize);
            pieces.add(content.substring(start, end));
            if (end == content.length()) {
                break;
            }
            start += step;
        }
        return pieces;
    }

    /**
     * 在窗口末尾附近优先找句号、问号、叹号、换行等软截断点。
     */
    private static List<String> splitFixedSoft(String content, int chunkSize, int overlap) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        if (content.length() <= chunkSize) {
            return List.of(content);
        }
        int step = Math.max(1, chunkSize - overlap);
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkSize);
            if (end < content.length()) {
                int searchFrom = Math.max(start + chunkSize / 2, end - Math.min(240, chunkSize / 3));
                int br = lastSoftBreak(content, searchFrom, end);
                if (br >= searchFrom) {
                    end = br + 1;
                }
            }
            String piece = content.substring(start, end).strip();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            if (end >= content.length()) {
                break;
            }
            start = end - overlap;
            if (start < 0) {
                start = 0;
            }
            if (start >= content.length()) {
                break;
            }
        }
        return pieces;
    }

    private static int lastSoftBreak(String s, int from, int toExclusive) {
        int best = -1;
        for (int i = toExclusive - 1; i >= from; i--) {
            char c = s.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' || c == '\n') {
                best = i;
                break;
            }
        }
        return best;
    }
}
