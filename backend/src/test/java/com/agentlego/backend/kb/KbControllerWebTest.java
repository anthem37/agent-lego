package com.agentlego.backend.kb;

import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.kb.application.KnowledgeBaseApplicationService;
import com.agentlego.backend.kb.application.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    void createBase_missingKbKey_shouldReturn400() throws Exception {
        mockMvc.perform(post("/kb/bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"N"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createBase_ok_shouldReturnCreated() throws Exception {
        KbBaseDto dto = new KbBaseDto();
        dto.setId("id1");
        dto.setKbKey("k1");
        dto.setName("N");
        when(knowledgeBaseApplicationService.createBase(any())).thenReturn(dto);

        mockMvc.perform(post("/kb/bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kbKey":"k1","name":"N","description":"d"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kbKey").value("k1"));
    }

    @Test
    void listBases_ok() throws Exception {
        when(knowledgeBaseApplicationService.listBases()).thenReturn(List.of());

        mockMvc.perform(get("/kb/bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void addKnowledge_ok() throws Exception {
        KbIngestResponse resp = new KbIngestResponse();
        resp.setDocumentId("d1");
        resp.setChunkCount(1);
        when(knowledgeBaseApplicationService.ingestKnowledge(eq("b1"), any())).thenReturn(resp);

        mockMvc.perform(post("/kb/bases/b1/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"doc1","content":"hi","chunkSize":800,"overlap":100}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.documentId").value("d1"));
    }

    @Test
    void listKnowledge_ok() throws Exception {
        KbDocumentPageDto page = KbDocumentPageDto.builder()
                .items(List.of())
                .total(0)
                .page(1)
                .pageSize(20)
                .build();
        when(knowledgeBaseApplicationService.listKnowledge(eq("b1"), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/kb/bases/b1/knowledge").param("page", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void query_ok() throws Exception {
        when(knowledgeBaseApplicationService.query(any())).thenReturn(new KbQueryResponse());

        mockMvc.perform(post("/kb/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kbKey":"k1","queryText":"hi","topK":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void deleteKnowledge_ok() throws Exception {
        mockMvc.perform(delete("/kb/knowledge/d1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        verify(knowledgeBaseApplicationService).deleteDocument("d1");
    }

    @Test
    void getKnowledge_ok() throws Exception {
        KbKnowledgeDetailDto dto = new KbKnowledgeDetailDto();
        dto.setId("d1");
        dto.setName("n");
        dto.setContentFormat("html");
        dto.setContentRich("<p>x</p>");
        when(knowledgeBaseApplicationService.getKnowledge("d1")).thenReturn(dto);

        mockMvc.perform(get("/kb/knowledge/d1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentFormat").value("html"))
                .andExpect(jsonPath("$.data.contentRich").value("<p>x</p>"));
    }
}
