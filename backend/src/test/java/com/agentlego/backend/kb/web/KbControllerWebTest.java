package com.agentlego.backend.kb.web;

import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.kb.application.dto.*;
import com.agentlego.backend.kb.application.service.KbApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

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
    private KbApplicationService kbApplicationService;

    @Test
    void chunkStrategies_meta_ok() throws Exception {
        mockMvc.perform(get("/kb/meta/chunk-strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].value").exists())
                .andExpect(jsonPath("$.data[0].label").exists());
    }

    @Test
    void createCollection_created_shouldReturnDto() throws Exception {
        KbCollectionDto dto = new KbCollectionDto();
        dto.setId("c1");
        dto.setName("n");
        dto.setDescription("");
        dto.setEmbeddingModelId("m1");
        dto.setEmbeddingDims(1536);
        dto.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        dto.setUpdatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        when(kbApplicationService.createCollection(any(CreateKbCollectionRequest.class))).thenReturn(dto);

        mockMvc.perform(
                        post("/kb/collections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"n\",\"description\":\"\",\"embeddingModelId\":\"m1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value("c1"))
                .andExpect(jsonPath("$.data.name").value("n"));

        verify(kbApplicationService).createCollection(any(CreateKbCollectionRequest.class));
    }

    @Test
    void ingestDocument_created_shouldReturnDto() throws Exception {
        KbDocumentDto dto = new KbDocumentDto();
        dto.setId("d1");
        dto.setCollectionId("c1");
        dto.setTitle("t");
        dto.setStatus("READY");
        dto.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        dto.setUpdatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        when(kbApplicationService.ingestTextDocument(eq("c1"), any(IngestKbDocumentRequest.class))).thenReturn(dto);

        mockMvc.perform(
                        post("/kb/collections/c1/documents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"t\",\"body\":\"hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value("d1"))
                .andExpect(jsonPath("$.data.status").value("READY"));

        verify(kbApplicationService).ingestTextDocument(eq("c1"), any(IngestKbDocumentRequest.class));
    }

    @Test
    void getDocument_ok_shouldReturnBody() throws Exception {
        KbDocumentDto dto = new KbDocumentDto();
        dto.setId("d1");
        dto.setCollectionId("c1");
        dto.setTitle("t");
        dto.setBody("hello-body");
        dto.setStatus("READY");
        dto.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        dto.setUpdatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        when(kbApplicationService.getDocument("c1", "d1")).thenReturn(dto);

        mockMvc.perform(get("/kb/collections/c1/documents/d1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.body").value("hello-body"));

        verify(kbApplicationService).getDocument("c1", "d1");
    }

    @Test
    void deleteDocument_ok_shouldCallService() throws Exception {
        mockMvc.perform(delete("/kb/collections/c1/documents/d1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(kbApplicationService).deleteDocument("c1", "d1");
    }

    @Test
    void deleteCollection_ok_shouldCallService() throws Exception {
        when(kbApplicationService.deleteCollection("c1")).thenReturn(new KbCollectionDeleteResult(2));

        mockMvc.perform(delete("/kb/collections/c1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.agentsPolicyUpdated").value(2));

        verify(kbApplicationService).deleteCollection("c1");
    }
}
