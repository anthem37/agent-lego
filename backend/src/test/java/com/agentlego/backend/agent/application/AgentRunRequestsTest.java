package com.agentlego.backend.agent.application;

import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentRunRequestsTest {

    @Test
    void of_twoArgs_doesNotSetNamespace() {
        RunAgentRequest r = AgentRunRequests.of("m1", "hello");
        assertEquals("m1", r.getModelId());
        assertEquals("hello", r.getInput());
        assertNull(r.getMemoryNamespace());
    }

    @Test
    void of_threeArgs_trimsNamespace() {
        RunAgentRequest r = AgentRunRequests.of("m1", "hello", "  ns-1  ");
        assertEquals("ns-1", r.getMemoryNamespace());
    }

    @Test
    void of_threeArgs_blankNamespaceIgnored() {
        RunAgentRequest r = AgentRunRequests.of("m1", "hello", "   ");
        assertNull(r.getMemoryNamespace());
    }
}
