package com.agentlego.backend.kb.support;

import com.agentlego.backend.kb.domain.KbChunkStrategyKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KbChunkExecutorTest {

    @Test
    void fixedWindow_overlap() {
        String t = "a".repeat(200);
        KbChunkExecutor ex = KbChunkExecutor.fromParsed(
                KbChunkStrategyKind.FIXED_WINDOW,
                Map.of("maxChars", 200, "overlap", 20)
        );
        List<String> c = ex.chunk(t);
        assertThat(c).isNotEmpty();
        assertThat(c.get(0).length()).isLessThanOrEqualTo(200);
    }

    @Test
    void paragraph_mergesBlocks() {
        String t = "第一段\n\n第二段\n\n第三段";
        KbChunkExecutor ex = KbChunkExecutor.fromParsed(
                KbChunkStrategyKind.PARAGRAPH,
                Map.of("maxChars", 256, "overlap", 0)
        );
        List<String> c = ex.chunk(t);
        assertThat(c).hasSize(1);
        assertThat(c.get(0)).contains("第一段");
    }

    @Test
    void fromStorage_roundTrip() {
        String json = KbChunkExecutor.normalizeParamsJson(
                KbChunkStrategyKind.FIXED_WINDOW,
                Map.of("maxChars", 512, "overlap", 40)
        );
        KbChunkExecutor ex = KbChunkExecutor.fromStorage("FIXED_WINDOW", json);
        assertThat(ex.chunk("a".repeat(600))).isNotEmpty();
    }

    @Test
    void headingSection_sliceContentKeepsHeadingLine() {
        String md = """
                # 手册
                
                ## 安装
                
                第一步说明
                第二步说明
                """;
        KbChunkExecutor ex = KbChunkExecutor.fromParsed(
                KbChunkStrategyKind.HEADING_SECTION,
                Map.of("headingLevel", 2, "leadMaxChars", 200, "maxChars", 1200, "overlap", 0)
        );
        List<KbChunkSlice> slices = ex.chunkSlices(md);
        assertThat(slices).isNotEmpty();
        KbChunkSlice first = slices.stream().filter(s -> s.content().contains("安装")).findFirst().orElseThrow();
        assertThat(first.content()).contains("## 安装");
        assertThat(first.embeddingText()).contains("安装");
        assertThat(first.metadata()).containsKey("sectionPath");
    }
}
