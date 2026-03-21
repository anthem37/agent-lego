package com.agentlego.backend.kb.support;

import com.agentlego.backend.kb.domain.KbChunkStrategyKind;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 根据集合配置对正文分片；策略说明见 {@link KbChunkStrategyKind}。
 */
public final class KbChunkExecutor {

    public static final int ABSOLUTE_MIN_MAX_CHARS = 128;
    public static final int ABSOLUTE_MAX_MAX_CHARS = 8192;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PARA_SPLIT = Pattern.compile("\\n\\s*\\n+");

    private final KbChunkStrategyKind strategy;
    private final int maxChars;
    private final int overlap;
    /**
     * HEADING_SECTION：1 或 2
     */
    private final int headingLevel;
    /**
     * HEADING_SECTION：引导段最大字符
     */
    private final int leadMaxChars;

    private KbChunkExecutor(
            KbChunkStrategyKind strategy,
            int maxChars,
            int overlap,
            int headingLevel,
            int leadMaxChars
    ) {
        this.strategy = strategy;
        this.maxChars = maxChars;
        this.overlap = overlap;
        this.headingLevel = headingLevel;
        this.leadMaxChars = leadMaxChars;
    }

    /**
     * 从持久化字段解析；非法 JSON 或越界参数抛出 {@link IllegalArgumentException}。
     */
    public static KbChunkExecutor fromStorage(String strategyCode, String paramsJson) {
        KbChunkStrategyKind st = KbChunkStrategyKind.fromApi(strategyCode == null ? "FIXED_WINDOW" : strategyCode);
        Map<String, Object> m;
        try {
            if (paramsJson == null || paramsJson.isBlank()) {
                m = Map.of();
            } else {
                m = MAPPER.readValue(paramsJson, new TypeReference<>() {
                });
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("chunk_params 不是合法 JSON：" + e.getMessage());
        }
        return fromParsed(st, m);
    }

    public static KbChunkExecutor fromParsed(KbChunkStrategyKind st, Map<String, Object> params) {
        int max = readInt(params, "maxChars", defaultMaxChars(st));
        int ov = readInt(params, "overlap", defaultOverlap(st));
        validateBounds(st, max, ov);
        int hl = 2;
        int ld = 512;
        if (st == KbChunkStrategyKind.HEADING_SECTION) {
            hl = readInt(params, "headingLevel", 2);
            if (hl != 1 && hl != 2) {
                throw new IllegalArgumentException("headingLevel 须为 1 或 2");
            }
            ld = readInt(params, "leadMaxChars", 512);
            if (ld < 64 || ld > 8192) {
                throw new IllegalArgumentException("leadMaxChars 须在 64～8192 之间");
            }
        }
        return new KbChunkExecutor(st, max, ov, hl, ld);
    }

    private static int defaultMaxChars(KbChunkStrategyKind st) {
        return switch (st) {
            case FIXED_WINDOW -> KbTextChunker.DEFAULT_MAX_CHARS;
            case PARAGRAPH, HEADING_SECTION -> 1200;
        };
    }

    private static int defaultOverlap(KbChunkStrategyKind st) {
        return switch (st) {
            case FIXED_WINDOW -> KbTextChunker.DEFAULT_OVERLAP;
            case PARAGRAPH, HEADING_SECTION -> 0;
        };
    }

    private static void validateBounds(KbChunkStrategyKind st, int max, int ov) {
        if (max < ABSOLUTE_MIN_MAX_CHARS || max > ABSOLUTE_MAX_MAX_CHARS) {
            throw new IllegalArgumentException(
                    "maxChars 须在 " + ABSOLUTE_MIN_MAX_CHARS + "～" + ABSOLUTE_MAX_MAX_CHARS + " 之间"
            );
        }
        if (ov < 0 || ov > max / 2) {
            throw new IllegalArgumentException("overlap 须在 0～maxChars/2 之间");
        }
        if (st == KbChunkStrategyKind.HEADING_SECTION && ov != 0) {
            throw new IllegalArgumentException("HEADING_SECTION 策略请将 overlap 设为 0（节内超长时再按 maxChars 切分）");
        }
    }

    private static int readInt(Map<String, Object> m, String key, int defaultVal) {
        Object v = m.get(key);
        if (v == null) {
            return defaultVal;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key + " 须为整数");
            }
        }
        throw new IllegalArgumentException(key + " 类型无效");
    }

    private static List<KbChunkSlice> mapSlices(List<String> parts) {
        return parts.stream().map(KbChunkSlice::uniform).toList();
    }

    static List<String> chunkParagraph(String text, int maxChars) {
        String[] paras = PARA_SPLIT.split(text);
        List<String> merged = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : paras) {
            String block = p.trim();
            if (block.isEmpty()) {
                continue;
            }
            if (cur.isEmpty()) {
                cur.append(block);
                continue;
            }
            if (cur.length() + 2 + block.length() <= maxChars) {
                cur.append("\n\n").append(block);
            } else {
                merged.add(cur.toString());
                cur = new StringBuilder(block);
            }
        }
        if (!cur.isEmpty()) {
            merged.add(cur.toString());
        }
        return splitOversize(merged, maxChars, 0);
    }

    private static List<String> splitOversize(List<String> parts, int maxChars, int overlap) {
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p.length() <= maxChars) {
                out.add(p);
            } else {
                out.addAll(KbTextChunker.chunk(p, maxChars, overlap));
            }
        }
        return out;
    }

    /**
     * 创建集合时把请求参数规范化为可持久化 JSON。
     */
    public static String normalizeParamsJson(KbChunkStrategyKind st, Map<String, Object> params) {
        KbChunkExecutor ex = fromParsed(st, params == null ? Map.of() : params);
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("maxChars", ex.maxChars);
            map.put("overlap", ex.overlap);
            if (st == KbChunkStrategyKind.HEADING_SECTION) {
                map.put("headingLevel", ex.headingLevel);
                map.put("leadMaxChars", ex.leadMaxChars);
            }
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 兼容旧 API：返回每段正文（与 {@link #chunkSlices(String)} 的 content 一致）。
     */
    public List<String> chunk(String text) {
        return chunkSlices(text).stream().map(KbChunkSlice::content).toList();
    }

    /**
     * 分片结果：含用于向量化与召回的正文、元数据（如章节路径）。
     */
    public List<KbChunkSlice> chunkSlices(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String t = text.trim();
        return switch (strategy) {
            case HEADING_SECTION -> KbHeadingSectionSplitter.split(t, headingLevel, leadMaxChars, maxChars, overlap);
            case FIXED_WINDOW -> mapSlices(KbTextChunker.chunk(t, maxChars, overlap));
            case PARAGRAPH -> mapSlices(chunkParagraph(t, maxChars));
        };
    }

    public KbChunkStrategyKind getStrategy() {
        return strategy;
    }

    public int getMaxChars() {
        return maxChars;
    }
}
