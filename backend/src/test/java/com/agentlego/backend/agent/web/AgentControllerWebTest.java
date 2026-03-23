package com.agentlego.backend.agent.web;

import com.agentlego.backend.agent.application.dto.AgentDto;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.application.service.AgentApplicationService;
import com.agentlego.backend.api.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AgentController.class)
@Import(GlobalExceptionHandler.class)
class AgentControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentApplicationService agentApplicationService;

    @Test
    void createAgent_missingName_shouldReturn400() throws Exception {
        mockMvc.perform(post("/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"systemPrompt":"SYS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createAgent_ok_shouldReturnCreated() throws Exception {
        when(agentApplicationService.createAgent(any())).thenReturn("a1");

        mockMvc.perform(post("/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"agent1","systemPrompt":"SYS","modelId":"m1","toolIds":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("created"))
                .andExpect(jsonPath("$.data").value("a1"));
    }

    @Test
    void getAgent_ok_shouldReturnDto() throws Exception {
        AgentDto dto = new AgentDto();
        dto.setId("a1");
        dto.setName("agent1");
        dto.setSystemPrompt("SYS");
        dto.setModelId("m1");
        dto.setToolIds(List.of("t1"));
        dto.setKnowledgeBasePolicy(Map.of());
        dto.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));

        when(agentApplicationService.getAgent("a1")).thenReturn(dto);

        mockMvc.perform(get("/agents/a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value("a1"));
    }

    @Test
    void runAgent_ok_shouldReturnOutput() throws Exception {
        RunAgentResponse resp = new RunAgentResponse();
        resp.setOutput("hello");
        when(agentApplicationService.runAgent(eq("a1"), any())).thenReturn(resp);

        mockMvc.perform(post("/agents/a1/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelId":"m1","input":"hi"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.output").value("hello"));
    }
}

