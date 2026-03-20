package com.agentlego.backend.model.support;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void test_modelStreamBlankFirstResponse_shouldReturnEmptyResponse() {
        ModelConnectivityTester tester = new ModelConnectivityTester("prompt", 16, Duration.ofSeconds(1));

        ChatResponse response = Mockito.mock(ChatResponse.class);
        TextBlock blank = TextBlock.builder().text("   ").build();
        when(response.getContent()).thenReturn(List.of(blank));

        Model model = Mockito.mock(Model.class);
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(response));

        String result = tester.test(model);
        assertNotNull(result);
        assertEquals("EMPTY_RESPONSE", result);
    }

    @Test
    void test_modelStreamOkResponse_shouldReturnTrimmedText() {
        ModelConnectivityTester tester = new ModelConnectivityTester("prompt", 16, Duration.ofSeconds(1));

        ChatResponse response = Mockito.mock(ChatResponse.class);
        TextBlock ok = TextBlock.builder().text("  OK  ").build();
        when(response.getContent()).thenReturn(List.of(ok));

        Model model = Mockito.mock(Model.class);
        when(model.stream(anyList(), anyList(), any())).thenReturn(Flux.just(response));

        assertEquals("OK", tester.test(model));
    }
}

