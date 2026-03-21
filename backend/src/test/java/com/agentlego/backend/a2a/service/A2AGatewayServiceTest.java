package com.agentlego.backend.a2a.service;

import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.application.service.AgentApplicationService;
import com.agentlego.backend.api.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class A2AGatewayServiceTest {

    @Mock
    private AgentApplicationService agentApplicationService;

    @Test
    void delegateLocal_missingAgentId_shouldThrowValidationError() {
        A2AGatewayService service = new A2AGatewayService(agentApplicationService);

        ApiException ex = assertThrows(ApiException.class, () -> service.delegateLocal("", "m1", "hi"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void delegateLocal_shouldDelegateToAgentAndReturnOutput() {
        A2AGatewayService service = new A2AGatewayService(agentApplicationService);

        RunAgentResponse agentResp = new RunAgentResponse();
        agentResp.setOutput("agent-out");
        when(agentApplicationService.runAgent(eq("a1"), any(RunAgentRequest.class))).thenReturn(agentResp);

        String out = service.delegateLocal("a1", "m1", "hi");
        assertEquals("agent-out", out);
    }
}

