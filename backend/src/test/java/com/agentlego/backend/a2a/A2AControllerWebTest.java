package com.agentlego.backend.a2a;

import com.agentlego.backend.api.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = A2AController.class)
@Import(GlobalExceptionHandler.class)
class A2AControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private A2AGatewayService gatewayService;

    @Test
    void delegate_missingAgentId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/a2a/delegate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelId":"m1","input":"hi"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void delegate_ok_shouldReturnOutput() throws Exception {
        when(gatewayService.delegateLocal(anyString(), anyString(), anyString())).thenReturn("out");

        mockMvc.perform(post("/a2a/delegate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentId":"a1","modelId":"m1","input":"hi"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value("out"));
    }
}

