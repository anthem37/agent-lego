package com.agentlego.backend.kb.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库入库：每条分片送往 embedding 模型的文本构造（标题前缀 + 分片正文 + 相似问块）。
 * <p>
 * 历史上将「相似问」拼在末尾后整体 {@code substring(0, 8000)}，长分片会把相似问整段截掉，导致召回侧「相似问没用」。
 * 此处优先保留相似问块，从分片正文侧截断；并对相似问总长度设上限，避免占满窗口。
 */
public final class KbIngestEmbeddingInputs {

    public static final int MAX_EMBEDDING_INPUT_CHARS = 8000;

    /**
     * 截断正文后至少保留的字符数，避免向量语义完全丢失
     */
    private static final int MIN_CORE_CHARS_PRESERVED = 256;

    /**
     * 「相似问」块（含 {@code \n\n相似问:\n} 前缀）最大长度；与 {@link #MAX_EMBEDDING_INPUT_CHARS} 配合保证正文有空间。
     */
    private static final int MAX_SIMILAR_BLOCK_CHARS = 2800;

    private KbIngestEmbeddingInputs() {
    }

    public static List<String> build(String documentTitle, List<KbChunkSlice> slices, List<String> similarQueries) {
        List<String> lines = normalizeSimilarQueries(similarQueries);
        String similarBlock = buildSimilarBlock(lines);
        String titlePrefix = buildTitlePrefix(documentTitle);
        List<String> out = new ArrayList<>(slices.size());
        for (KbChunkSlice s : slices) {
            String core = titlePrefix + s.embeddingText();
            out.add(combinePreservingSimilarBlock(core, similarBlock, MAX_EMBEDDING_INPUT_CHARS));
        }
        return out;
    }

    /**
     * 与入库持久化、向量化共用：去空、长度与条数上限。
     */
    public static List<String> normalizeSimilarQueries(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (!t.isEmpty() && t.length() <= 512) {
                out.add(t);
            }
            if (out.size() >= 32) {
                break;
            }
        }
        return out;
    }

    private static String buildTitlePrefix(String documentTitle) {
        if (documentTitle == null || documentTitle.isBlank()) {
            return "";
        }
        return "文档标题: " + documentTitle.trim() + "\n\n";
    }

    /**
     * 构造 {@code \n\n相似问:\n...}，总长度不超过 {@link #MAX_SIMILAR_BLOCK_CHARS}。
     */
    static String buildSimilarBlock(List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        String header = "\n\n相似问:\n";
        int maxBody = Math.max(0, MAX_SIMILAR_BLOCK_CHARS - header.length());
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            String sep = body.length() > 0 ? "\n" : "";
            String candidate = sep + line;
            if (body.length() + candidate.length() <= maxBody) {
                body.append(candidate);
                continue;
            }
            if (body.length() == 0) {
                body.append(line, 0, Math.min(line.length(), maxBody));
            }
            break;
        }
        return header + body;
    }

    /**
     * 总长限制为 maxTotal：优先保留 {@code similarBlock}（从块首截断若仍超限），再从 {@code core} 尾部截断。
     */
    static String combinePreservingSimilarBlock(String core, String similarBlock, int maxTotal) {
        if (similarBlock.isEmpty()) {
            return core.length() <= maxTotal ? core : core.substring(0, maxTotal);
        }
        if (core.length() + similarBlock.length() <= maxTotal) {
            return core + similarBlock;
        }
        int maxCore = maxTotal - similarBlock.length();
        if (maxCore >= MIN_CORE_CHARS_PRESERVED) {
            return core.substring(0, maxCore) + similarBlock;
        }
        // 相似问块仍偏长：先压缩块，再截正文
        int targetSuffix = Math.min(similarBlock.length(), maxTotal - MIN_CORE_CHARS_PRESERVED);
        if (targetSuffix <= 0) {
            return core.length() <= maxTotal ? core : core.substring(0, maxTotal);
        }
        String suffix = similarBlock.length() <= targetSuffix
                ? similarBlock
                : similarBlock.substring(0, targetSuffix);
        maxCore = maxTotal - suffix.length();
        int keepCore = Math.min(core.length(), Math.max(MIN_CORE_CHARS_PRESERVED, maxCore));
        return core.substring(0, keepCore) + suffix;
    }
}
