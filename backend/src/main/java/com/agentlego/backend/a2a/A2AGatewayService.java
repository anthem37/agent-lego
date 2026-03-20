package com.agentlego.backend.a2a;

import com.agentlego.backend.agent.application.AgentApplicationService;
import com.agentlego.backend.agent.application.AgentRunRequests;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.api.ApiRequires;
import org.springframework.stereotype.Service;

/**
 * A2A 网关服务。
 * <p>
 * 当前实现说明：
 * - 仅提供“本地委派”：把请求转为平台内部的 runAgent 调用；
 * - 后续将对接 A2A 标准的 JSON-RPC 与 streaming transport。
 */
@Service
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
        String safeAgentId = ApiRequires.nonBlank(agentId, "agentId");
        String safeModelId = ApiRequires.nonBlank(modelId, "modelId");
        String safeInput = (input == null) ? "" : input;

        RunAgentResponse resp = agentApplicationService.runAgent(safeAgentId, AgentRunRequests.of(safeModelId, safeInput));
        return (resp == null || resp.getOutput() == null) ? "" : resp.getOutput();
    }
}
