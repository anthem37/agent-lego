package com.agentlego.backend.kb.application.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前端下拉：可选分片策略及默认参数说明。
 */
public record KbChunkStrategyMetaDto(
        String value,
        String label,
        String description,
        Map<String, Object> defaultParams
) {
    public static List<KbChunkStrategyMetaDto> standardList() {
        return List.of(
                new KbChunkStrategyMetaDto(
                        "FIXED_WINDOW",
                        "固定窗口 + 重叠",
                        "按字符长度滑动窗口，相邻块可重叠，适合通用长文与无结构文本。",
                        mapOf(
                                "maxChars", 900,
                                "overlap", 120
                        )
                ),
                new KbChunkStrategyMetaDto(
                        "PARAGRAPH",
                        "段落优先",
                        "按空行分段后再合并到不超过 maxChars，适合 Markdown、制度、说明书。",
                        mapOf("maxChars", 1200, "overlap", 0)
                ),
                new KbChunkStrategyMetaDto(
                        "HEADING_SECTION",
                        "按标题分节（Markdown）",
                        "按指定级别标题（如 ##）切节；向量用「章节路径 + 标题 + 引导段」，召回用整节正文。overlap 须为 0。",
                        mapOf(
                                "headingLevel", 2,
                                "leadMaxChars", 512,
                                "maxChars", 1200,
                                "overlap", 0
                        )
                )
        );
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
