package com.agentlego.backend.kb.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KbChunkSimilarQueriesTest {

    @Test
    void parsesLinesAfterMarker() {
        String raw = "正文片段\n\n相似问:\n怎么退款\n运费多少";
        assertEquals(List.of("怎么退款", "运费多少"), KbChunkSimilarQueries.parseFromChunkText(raw));
    }

    @Test
    void emptyWhenNoMarker() {
        assertTrue(KbChunkSimilarQueries.parseFromChunkText("只有正文").isEmpty());
    }
}
