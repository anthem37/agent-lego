package com.agentlego.backend.a2a;

import com.agentlego.backend.agent.application.AgentApplicationService;
import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
/**
 * A2A 网关服务。
 *
 * 当前实现说明：
 * - 仅提供“本地委派”：把请求转为平台内部的 runAgent 调用；
 * - 后续将对接 A2A 标准的 JSON-RPC 与 streaming transport。
 */
public class A2AGatewayService {

    private final AgentApplicationService agentApplicationService;

    public A2AGatewayService(AgentApplicationService agentApplicationService) {
        this.agentApplicationService = agentApplicationService;
    }

    /**
     * 本地委派（local delegate）。
     * <p>
     * 说明：这是最小可用能力，便于先把端到端调用链跑通。
     */
    public String delegateLocal(String agentId, String modelId, String input) {
        String safeAgentId = requireNonBlank(agentId, "agentId");
        String safeModelId = requireNonBlank(modelId, "modelId");
        String safeInput = (input == null) ? "" : input;

        RunAgentRequest req = new RunAgentRequest();
        req.setModelId(safeModelId);
        req.setInput(safeInput);

        RunAgentResponse resp = agentApplicationService.runAgent(safeAgentId, req);
        return (resp == null || resp.getOutput() == null) ? "" : resp.getOutput();
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", fieldName + " is required", HttpStatus.BAD_REQUEST);
        }
        return value;
    }
}

