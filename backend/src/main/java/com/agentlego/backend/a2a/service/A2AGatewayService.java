package com.agentlego.backend.a2a.service;

import com.agentlego.backend.agent.application.AgentRunRequests;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.application.service.AgentApplicationService;
import com.agentlego.backend.api.ApiRequires;
import org.springframework.stereotype.Service;

/**
 * A2A 网关应用服务：当前为本地委派（转调 {@link AgentApplicationService}）。
 */
@Service
public class A2AGatewayService {

    private final AgentApplicationService agentApplicationService;

    public A2AGatewayService(AgentApplicationService agentApplicationService) {
        this.agentApplicationService = agentApplicationService;
    }

    public String delegateLocal(String agentId, String modelId, String input, String memoryNamespace) {
        String safeAgentId = ApiRequires.nonBlank(agentId, "agentId");
        String safeModelId = ApiRequires.nonBlank(modelId, "modelId");
        String safeInput = (input == null) ? "" : input;

        RunAgentResponse resp = agentApplicationService.runAgent(
                safeAgentId,
                AgentRunRequests.of(safeModelId, safeInput, memoryNamespace)
        );
        return (resp == null || resp.getOutput() == null) ? "" : resp.getOutput();
    }
}
