package com.agentlego.backend.memory;

import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.memory.application.MemoryApplicationService;
import com.agentlego.backend.memory.application.dto.MemoryQueryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MemoryController.class)
@Import(GlobalExceptionHandler.class)
class MemoryControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemoryApplicationService memoryApplicationService;

    @Test
    void createItem_missingOwnerScope_shouldReturn400() throws Exception {
        mockMvc.perform(post("/memory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"hi","metadata":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createItem_ok_shouldReturnCreated() throws Exception {
        when(memoryApplicationService.createItem(any())).thenReturn("mem1");

        mockMvc.perform(post("/memory/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerScope":"user1","content":"hi","metadata":{}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("created"))
                .andExpect(jsonPath("$.data").value("mem1"));
    }

    @Test
    void query_ok_shouldReturnItems() throws Exception {
        MemoryQueryResponse resp = new MemoryQueryResponse();
        resp.setItems(List.of());
        when(memoryApplicationService.query(any())).thenReturn(resp);

        mockMvc.perform(post("/memory/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerScope":"user1","queryText":"hi","topK":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items").isArray());
    }
}

