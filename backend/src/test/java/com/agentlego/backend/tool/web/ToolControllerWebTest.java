package com.agentlego.backend.tool.web;

import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.tool.application.dto.*;
import com.agentlego.backend.tool.application.service.ToolApplicationService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ToolController.class, ToolMetaController.class})
@Import(GlobalExceptionHandler.class)
class ToolControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ToolApplicationService toolApplicationService;

    @Test
    void createTool_missingToolType_shouldReturn400() throws Exception {
        mockMvc.perform(post("/tools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"my_tool","definition":{}}
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
                                {"toolType":"LOCAL","name":"my_tool","definition":{}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("created"))
                .andExpect(jsonPath("$.data").value("t1"));
    }

    @Test
    void listTools_ok_shouldReturnPage() throws Exception {
        ToolDto dto = new ToolDto();
        dto.setId("t1");
        dto.setToolType("LOCAL");
        dto.setName("my_tool");
        dto.setDefinition(Map.of());
        dto.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));

        when(toolApplicationService.listToolsPage(1, 50, null, null)).thenReturn(
                ToolPageDto.builder()
                        .items(List.of(dto))
                        .total(1)
                        .page(1)
                        .pageSize(50)
                        .build()
        );

        mockMvc.perform(get("/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[0].id").value("t1"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(toolApplicationService).listToolsPage(eq(1), eq(50), isNull(), isNull());
    }

    @Test
    void listTools_withToolType_shouldPassParam() throws Exception {
        when(toolApplicationService.listToolsPage(1, 20, null, "MCP")).thenReturn(
                ToolPageDto.builder()
                        .items(List.of())
                        .total(0)
                        .page(1)
                        .pageSize(20)
                        .build()
        );

        mockMvc.perform(get("/tools").param("page", "1").param("pageSize", "20").param("toolType", "MCP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(0));

        verify(toolApplicationService).listToolsPage(eq(1), eq(20), isNull(), eq("MCP"));
    }

    @Test
    void toolTypeMeta_ok_shouldReturnList() throws Exception {
        when(toolApplicationService.listToolTypeMeta()).thenReturn(List.of(
                ToolTypeMetaDto.builder().code("LOCAL").label("本地").description("d").supportsTestCall(true).build()
        ));

        mockMvc.perform(get("/tools/meta/tool-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].code").value("LOCAL"));
    }

    @Test
    void localBuiltinsMeta_ok_shouldReturnList() throws Exception {
        when(toolApplicationService.listLocalBuiltins()).thenReturn(List.of(
                LocalBuiltinToolMetaDto.builder()
                        .name("t1")
                        .label("t1")
                        .description("d")
                        .usageHint("h")
                        .build()
        ));

        mockMvc.perform(get("/tools/meta/local-builtins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].name").value("t1"));
    }

    @Test
    void remoteMcpTools_ok_shouldReturnList() throws Exception {
        when(toolApplicationService.listRemoteMcpTools("http://127.0.0.1:9/mcp", false))
                .thenReturn(List.of(RemoteMcpToolMetaDto.builder().name("t1").description("d").build()));

        mockMvc.perform(get("/tools/meta/mcp/remote-tools")
                        .param("endpoint", "http://127.0.0.1:9/mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].name").value("t1"));

        verify(toolApplicationService).listRemoteMcpTools("http://127.0.0.1:9/mcp", false);
    }

    @Test
    void batchImportMcpTools_ok_shouldReturnResult() throws Exception {
        BatchImportMcpToolsResponse resp = BatchImportMcpToolsResponse.builder()
                .created(List.of(BatchImportMcpToolsResponse.Created.builder()
                        .id("id1")
                        .name("mcp_t1")
                        .remoteToolName("t1")
                        .build()))
                .skipped(List.of())
                .nameConflicts(List.of())
                .build();
        when(toolApplicationService.batchImportMcpTools(any())).thenReturn(resp);

        mockMvc.perform(post("/tools/meta/mcp/batch-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endpoint":"http://127.0.0.1:9/mcp","remoteToolNames":["t1"],"skipExisting":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.created[0].id").value("id1"));

        verify(toolApplicationService).batchImportMcpTools(any());
    }

    @Test
    void updateTool_ok_shouldCallService() throws Exception {
        mockMvc.perform(put("/tools/t1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toolType":"LOCAL","name":"my_tool","definition":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(toolApplicationService).updateTool(eq("t1"), any(UpdateToolRequest.class));
    }

    @Test
    void deleteTool_ok_shouldCallService() throws Exception {
        mockMvc.perform(delete("/tools/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(toolApplicationService).deleteTool("t1");
    }

    @Test
    void toolReferences_ok_shouldReturnData() throws Exception {
        ToolReferencesDto dto = new ToolReferencesDto();
        dto.setReferencingAgentCount(1);
        dto.setReferencingAgentIds(List.of("agent-1"));
        dto.setReferencingKbDocumentCount(3L);
        when(toolApplicationService.getToolReferences("t1")).thenReturn(dto);

        mockMvc.perform(get("/tools/t1/references"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.referencingAgentCount").value(1))
                .andExpect(jsonPath("$.data.referencingAgentIds[0]").value("agent-1"))
                .andExpect(jsonPath("$.data.referencingKbDocumentCount").value(3));
    }
}

