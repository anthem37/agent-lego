package com.agentlego.backend.tool;

import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.tool.application.ToolApplicationService;
import com.agentlego.backend.tool.application.dto.ToolDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ToolController.class)
@Import(GlobalExceptionHandler.class)
class ToolControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ToolApplicationService toolApplicationService;

    @Test
    void createTool_missingToolType_shouldReturn400() throws Exception {
        mockMvc.perform(post("/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"echo","definition":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createTool_ok_shouldReturnCreated() throws Exception {
        when(toolApplicationService.createTool(any())).thenReturn("t1");

        mockMvc.perform(post("/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toolType":"LOCAL","name":"echo","definition":{}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("created"))
                .andExpect(jsonPath("$.data").value("t1"));
    }

    @Test
    void listTools_ok_shouldReturnList() throws Exception {
        ToolDto dto = new ToolDto();
        dto.setId("t1");
        dto.setToolType("LOCAL");
        dto.setName("echo");
        dto.setDefinition(Map.of());
        dto.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));

        when(toolApplicationService.listTools()).thenReturn(List.of(dto));

        mockMvc.perform(get("/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value("t1"));
    }
}

