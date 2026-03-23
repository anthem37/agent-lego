package com.agentlego.backend.memorypolicy.web;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.memorypolicy.application.dto.MemoryPolicyDto;
import com.agentlego.backend.memorypolicy.application.dto.MemoryReindexVectorsResultDto;
import com.agentlego.backend.memorypolicy.application.service.MemoryPolicyApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MemoryPolicyController.class)
@Import(GlobalExceptionHandler.class)
class MemoryPolicyControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemoryPolicyApplicationService memoryPolicyApplicationService;

    @Test
    void listPolicies_ok_shouldReturnArray() throws Exception {
        MemoryPolicyDto dto = new MemoryPolicyDto();
        dto.setId("p1");
        dto.setName("策略一");
        dto.setOwnerScope("ns");
        when(memoryPolicyApplicationService.listPolicies()).thenReturn(List.of(dto));

        mockMvc.perform(get("/memory-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value("p1"))
                .andExpect(jsonPath("$.data[0].name").value("策略一"));

        verify(memoryPolicyApplicationService).listPolicies();
    }

    @Test
    void getPolicy_ok_shouldReturnDto() throws Exception {
        MemoryPolicyDto dto = new MemoryPolicyDto();
        dto.setId("p1");
        dto.setName("策略一");
        dto.setOwnerScope("ns");
        when(memoryPolicyApplicationService.getPolicy("p1")).thenReturn(dto);

        mockMvc.perform(get("/memory-policies/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value("p1"))
                .andExpect(jsonPath("$.data.ownerScope").value("ns"));

        verify(memoryPolicyApplicationService).getPolicy("p1");
    }

    @Test
    void deletePolicy_ok_shouldCallService() throws Exception {
        mockMvc.perform(delete("/memory-policies/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(memoryPolicyApplicationService).deletePolicy("p1");
    }

    @Test
    void reindexVectors_ok_shouldReturnIndexedCount() throws Exception {
        MemoryReindexVectorsResultDto dto = new MemoryReindexVectorsResultDto();
        dto.setIndexedCount(7);
        when(memoryPolicyApplicationService.reindexVectors(eq("pol-1"))).thenReturn(dto);

        mockMvc.perform(post("/memory-policies/pol-1/reindex-vectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.indexedCount").value(7))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verify(memoryPolicyApplicationService).reindexVectors("pol-1");
    }

    @Test
    void reindexVectors_notFound_shouldReturn404AndErrorBody() throws Exception {
        when(memoryPolicyApplicationService.reindexVectors(eq("missing")))
                .thenThrow(new ApiException("NOT_FOUND", "记忆策略未找到", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/memory-policies/missing/reindex-vectors"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("记忆策略未找到"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verify(memoryPolicyApplicationService).reindexVectors("missing");
    }

    @Test
    void reindexVectors_invalidMode_shouldReturn400AndErrorBody() throws Exception {
        when(memoryPolicyApplicationService.reindexVectors(eq("pol-kw")))
                .thenThrow(new ApiException(
                        "INVALID_MEMORY_POLICY",
                        "仅 retrievalMode 为 VECTOR 或 HYBRID 时可重索引",
                        HttpStatus.BAD_REQUEST
                ));

        mockMvc.perform(post("/memory-policies/pol-kw/reindex-vectors"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEMORY_POLICY"))
                .andExpect(jsonPath("$.message").value("仅 retrievalMode 为 VECTOR 或 HYBRID 时可重索引"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(memoryPolicyApplicationService).reindexVectors("pol-kw");
    }
}
