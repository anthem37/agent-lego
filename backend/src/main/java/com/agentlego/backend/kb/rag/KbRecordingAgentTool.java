package com.agentlego.backend.kb.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

/**
 * 装饰 {@link AgentTool}：在成功返回时将结果根对象写入 {@link KbRagSessionToolOutputs}（按平台工具 ID）。
 */
public final class KbRecordingAgentTool implements AgentTool {

    private final AgentTool delegate;
    private final String platformToolId;
    private final KbRagSessionToolOutputs sessionToolOutputs;
    private final ObjectMapper objectMapper;

    public KbRecordingAgentTool(
            AgentTool delegate,
            String platformToolId,
            KbRagSessionToolOutputs sessionToolOutputs,
            ObjectMapper objectMapper
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.platformToolId = Objects.requireNonNull(platformToolId, "platformToolId").trim();
        this.sessionToolOutputs = Objects.requireNonNull(sessionToolOutputs, "sessionToolOutputs");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public Map<String, Object> getParameters() {
        return delegate.getParameters();
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return delegate.callAsync(param)
                .doOnNext(block -> {
                    if (block != null && block.getId() != null && block.getName() != null) {
                        sessionToolOutputs.recordSuccess(platformToolId, block, objectMapper);
                    }
                });
    }
}
