package com.agentlego.backend.model.support;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class ModelConnectivityTesterTest {

    @Test
    void extractText_nullResponse_shouldReturnEmpty() {
        assertEquals("", ModelConnectivityTester.extractText(null));
    }

    @Test
    void extractText_textBlockList_shouldJoinTexts() {
        ChatResponse response = Mockito.mock(ChatResponse.class);
        TextBlock ok = TextBlock.builder().text("OK").build();
        TextBlock blank = TextBlock.builder().text("  ").build();
        when(response.getContent()).thenReturn(List.of(ok, blank));

        assertEquals("OK", ModelConnectivityTester.extractText(response));
    }

    @Test
    void extractText_thinkingBlock_shouldUseThinkingText() {
        ChatResponse response = Mockito.mock(ChatResponse.class);
        ThinkingBlock think = ThinkingBlock.builder().thinking("inner reasoning").build();
        when(response.getContent()).thenReturn(List.of(think));

        assertEquals("inner reasoning", ModelConnectivityTester.extractText(response));
    }

    @Test
    void testChat_thinkingOnly_shouldNotBeEffectivelyEmpty() {
        ModelConnectivityTester tester = new ModelConnectivityTester("prompt", 256, 32, Duration.ofSeconds(1));

        ChatResponse response = Mockito.mock(ChatResponse.class);
        ThinkingBlock think = ThinkingBlock.builder().thinking("visible thought").build();
        when(response.getContent()).thenReturn(List.of(think));

        Model model = Mockito.mock(Model.class);
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(response));

        ModelConnectivityTester.ChatConnectivityResult r = tester.testChat(model, null, null, null);
        assertEquals("visible thought", r.aggregatedText());
        assertTrue(!r.isEffectivelyEmpty());
    }

    @Test
    void testChat_firstChunkBlank_shouldBeEffectivelyEmpty() {
        ModelConnectivityTester tester = new ModelConnectivityTester("prompt", 256, 32, Duration.ofSeconds(1));

        ChatResponse response = Mockito.mock(ChatResponse.class);
        TextBlock blank = TextBlock.builder().text("   ").build();
        when(response.getContent()).thenReturn(List.of(blank));

        Model model = Mockito.mock(Model.class);
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(response));

        ModelConnectivityTester.ChatConnectivityResult r = tester.testChat(model, null, null, null);
        assertTrue(r.isEffectivelyEmpty());
        assertEquals(1, r.streamChunks());
    }

    @Test
    void testChat_okResponse_shouldAggregateText() {
        ModelConnectivityTester tester = new ModelConnectivityTester("prompt", 256, 32, Duration.ofSeconds(1));

        ChatResponse response = Mockito.mock(ChatResponse.class);
        TextBlock ok = TextBlock.builder().text("  OK  ").build();
        when(response.getContent()).thenReturn(List.of(ok));

        Model model = Mockito.mock(Model.class);
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(response));

        ModelConnectivityTester.ChatConnectivityResult r = tester.testChat(model, null, null, null);
        assertEquals("OK", r.aggregatedText());
        assertEquals("prompt", r.promptUsed());
        assertEquals(256, r.maxTokensUsed());
    }
}
