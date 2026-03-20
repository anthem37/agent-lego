package com.agentlego.backend.kb;

import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.kb.application.KnowledgeBaseApplicationService;
import com.agentlego.backend.kb.application.dto.KbIngestResponse;
import com.agentlego.backend.kb.application.dto.KbQueryResponse;
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

@WebMvcTest(controllers = KbController.class)
@Import(GlobalExceptionHandler.class)
class KbControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeBaseApplicationService knowledgeBaseApplicationService;

    @Test
    void ingest_missingKbKey_shouldReturn400() throws Exception {
        mockMvc.perform(post("/kb/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"doc1","content":"hi"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void ingest_ok_shouldReturnCreated() throws Exception {
        KbIngestResponse resp = new KbIngestResponse();
        resp.setDocumentId("doc1");
        when(knowledgeBaseApplicationService.ingest(any())).thenReturn(resp);

        mockMvc.perform(post("/kb/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kbKey":"kb1","name":"doc1","content":"hi","chunkSize":800,"overlap":100}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("created"))
                .andExpect(jsonPath("$.data.documentId").value("doc1"));
    }

    @Test
    void query_ok_shouldReturnChunks() throws Exception {
        KbQueryResponse resp = new KbQueryResponse();
        resp.setChunks(List.of());
        when(knowledgeBaseApplicationService.query(any())).thenReturn(resp);

        mockMvc.perform(post("/kb/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kbKey":"kb1","queryText":"hi","topK":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.chunks").isArray());
    }
}

