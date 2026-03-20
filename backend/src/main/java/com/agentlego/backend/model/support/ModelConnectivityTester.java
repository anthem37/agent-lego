package com.agentlego.backend.model.support;

import cn.hutool.core.util.StrUtil;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 对 AgentScope {@link Model} 做连通性 / 能力测试。
 * <p>
 * 采集多条流式 chunk（可配置上限），汇总文本并统计耗时。
 */
@Component
public class ModelConnectivityTester {

    public static final String EMPTY_RESPONSE = "EMPTY_RESPONSE";

    private static final String USER_MESSAGE_NAME = "user";

    private final String defaultTestPrompt;
    private final int defaultMaxTokens;
    private final int defaultMaxStreamChunks;
    private final Duration timeout;

    public ModelConnectivityTester(
            @Value("${model.connectivity.testPrompt:Reply with a single word: OK.}") String defaultTestPrompt,
            @Value("${model.connectivity.maxTokens:256}") int defaultMaxTokens,
            @Value("${model.connectivity.maxStreamChunks:32}") int defaultMaxStreamChunks,
            @Value("${model.connectivity.timeout:PT2M}") Duration timeout
    ) {
        this.defaultTestPrompt = defaultTestPrompt;
        this.defaultMaxTokens = defaultMaxTokens;
        this.defaultMaxStreamChunks = Math.min(128, Math.max(1, defaultMaxStreamChunks));
        this.timeout = timeout;
    }

    static String extractText(ChatResponse response) {
        if (response == null || response.getContent() == null) {
            return "";
        }
        return response.getContent().stream()
                .filter(TextBlock.class::isInstance)
                .map(b -> ((TextBlock) b).getText())
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.joining(""));
    }

    private static String rootHint(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String m = cur.getMessage();
        if (StrUtil.isNotBlank(m)) {
            return m;
        }
        return cur.getClass().getSimpleName();
    }

    /**
     * 发送一条（或覆盖）用户消息，采集至多 {@code maxStreamChunks} 条流式响应并拼接文本。
     */
    public ChatConnectivityResult testChat(
            Model model,
            String promptOverride,
            Integer maxTokensOverride,
            Integer maxStreamChunksOverride
    ) {
        Objects.requireNonNull(model, "model");
        String prompt = StrUtil.isNotBlank(promptOverride) ? promptOverride.trim() : defaultTestPrompt;
        if (prompt.length() > 8000) {
            prompt = prompt.substring(0, 8000);
        }
        int maxTok = maxTokensOverride != null ? maxTokensOverride : defaultMaxTokens;
        maxTok = Math.min(8192, Math.max(1, maxTok));
        int chunksCap = maxStreamChunksOverride != null ? maxStreamChunksOverride : defaultMaxStreamChunks;
        chunksCap = Math.min(128, Math.max(1, chunksCap));

        Msg userMsg = Msg.builder().name(USER_MESSAGE_NAME).textContent(prompt).build();
        List<Msg> messages = List.of(userMsg);
        GenerateOptions options = GenerateOptions.builder().maxTokens(maxTok).build();

        Flux<ChatResponse> flux = model.stream(messages, List.of(), options);
        long t0 = System.nanoTime();
        try {
            List<ChatResponse> list = flux.take(chunksCap).collectList().block(timeout);
            long latencyMs = (System.nanoTime() - t0) / 1_000_000L;
            if (list == null || list.isEmpty()) {
                return new ChatConnectivityResult("", latencyMs, 0, null, prompt, maxTok);
            }
            int chunks = list.size();
            String joined = list.stream()
                    .map(ModelConnectivityTester::extractText)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.joining(""));
            return new ChatConnectivityResult(joined, latencyMs, chunks, null, prompt, maxTok);
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - t0) / 1_000_000L;
            String hint = rootHint(ex);
            return new ChatConnectivityResult("", latencyMs, 0, hint, prompt, maxTok);
        }
    }

    /**
     * 聊天模型测试结果。
     *
     * @param errorMessage 非空表示调用失败（超时、网络、反序列化等）
     */
    public record ChatConnectivityResult(
            String aggregatedText,
            long latencyMillis,
            int streamChunks,
            String errorMessage,
            String promptUsed,
            int maxTokensUsed
    ) {
        public boolean isError() {
            return StrUtil.isNotBlank(errorMessage);
        }

        public boolean isEffectivelyEmpty() {
            return !isError() && StrUtil.isBlank(aggregatedText);
        }
    }
}
