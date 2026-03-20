package com.agentlego.backend.model.support;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 对 AgentScope {@link Model} 做轻量连通性测试。
 * <p>
 * 直接调用 model.stream()，不依赖 AgentRuntime，符合单一职责。
 */
@Component
public class ModelConnectivityTester {

    private static final String TEST_PROMPT = "Reply with a single word: OK.";
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private static String extractText(ChatResponse response) {
        if (response == null || response.getContent() == null) {
            return "";
        }
        return response.getContent().stream()
                .filter(TextBlock.class::isInstance)
                .map(b -> ((TextBlock) b).getText())
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" "));
    }

    /**
     * 发送一条最小请求，取首个响应的文本作为结果。
     *
     * @return 首块文本内容，若为空则返回 "EMPTY_RESPONSE"
     */
    public String test(Model model) {
        Objects.requireNonNull(model, "model");
        Msg userMsg = Msg.builder().name("user").textContent(TEST_PROMPT).build();
        List<Msg> messages = List.of(userMsg);
        GenerateOptions options = GenerateOptions.builder().maxTokens(16).build();

        Flux<ChatResponse> flux = model.stream(messages, List.of(), options);
        ChatResponse first = flux.blockFirst(TIMEOUT);
        if (first == null) {
            return "EMPTY_RESPONSE";
        }
        String text = extractText(first);
        return (text == null || text.isBlank()) ? "EMPTY_RESPONSE" : text.trim();
    }
}
