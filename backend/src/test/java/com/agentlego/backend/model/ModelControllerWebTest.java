package com.agentlego.backend.model;

import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.model.application.ModelApplicationService;
import com.agentlego.backend.model.dto.ModelDto;
import com.agentlego.backend.model.dto.TestModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ModelController.class)
@Import(GlobalExceptionHandler.class)
class ModelControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelApplicationService modelApplicationService;

    @Test
    void createModel_missingProvider_shouldReturn400() throws Exception {
        mockMvc.perform(post("/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modelKey":"qwen-max","apiKey":"k"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createModel_ok_shouldReturnCreatedAndId() throws Exception {
        when(modelApplicationService.createModel(any())).thenReturn("m1");

        mockMvc.perform(post("/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"DASHSCOPE","modelKey":"qwen-max","apiKey":"k","config":{"temperature":0.1}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("created"))
                .andExpect(jsonPath("$.data").value("m1"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void getModel_ok_shouldReturnDto() throws Exception {
        ModelDto dto = new ModelDto();
        dto.setId("m1");
        dto.setProvider("DASHSCOPE");
        dto.setModelKey("qwen-max");
        dto.setConfig(Map.of("temperature", 0.1));
        dto.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));

        when(modelApplicationService.getModel("m1")).thenReturn(dto);

        mockMvc.perform(get("/models/m1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value("m1"))
                .andExpect(jsonPath("$.data.provider").value("DASHSCOPE"));
    }

    @Test
    void testModel_ok_shouldReturnResponse() throws Exception {
        when(modelApplicationService.testModel("m1"))
                .thenReturn(new TestModelResponse("OK", "OK"));

        mockMvc.perform(post("/models/m1/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.message").value("OK"));
    }
}

