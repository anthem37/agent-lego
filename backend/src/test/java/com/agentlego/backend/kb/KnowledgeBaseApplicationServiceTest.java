package com.agentlego.backend.kb;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.application.KnowledgeBaseApplicationService;
import com.agentlego.backend.kb.application.dto.CreateKbDocumentRequest;
import com.agentlego.backend.kb.application.dto.KbIngestResponse;
import com.agentlego.backend.kb.domain.KbChunkAggregate;
import com.agentlego.backend.kb.domain.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * KnowledgeBaseApplicationService 单元测试。
 * <p>
 * 覆盖点：
 * - ingest 的参数校验（chunkSize/overlap）
 * - 分片逻辑（短文本/重叠分片）与 chunkIndex 递增约束
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseApplicationServiceTest {

    @Mock
    private KnowledgeBaseRepository repository;

    @Test
    void ingest_chunkSizeLessOrEqualOverlap_shouldThrowValidationError() {
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);

        CreateKbDocumentRequest req = new CreateKbDocumentRequest();
        req.setKbKey("kb1");
        req.setName("doc1");
        req.setContent("abcdefg");
        req.setChunkSize(5);
        req.setOverlap(5);

        ApiException ex = assertThrows(ApiException.class, () -> service.ingest(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void ingest_shortContent_shouldCreateSingleChunk() {
        when(repository.ensureDocument(eq("kb1"), eq("doc1"))).thenReturn("docId1");

        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);

        CreateKbDocumentRequest req = new CreateKbDocumentRequest();
        req.setKbKey("kb1");
        req.setName("doc1");
        req.setContent("abc");
        req.setChunkSize(10);
        req.setOverlap(1);

        KbIngestResponse resp = service.ingest(req);
        assertEquals("docId1", resp.getDocumentId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KbChunkAggregate>> chunksCaptor = (ArgumentCaptor<List<KbChunkAggregate>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(repository, times(1)).saveChunks(eq("docId1"), chunksCaptor.capture());

        List<KbChunkAggregate> chunks = chunksCaptor.getValue();
        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals("abc", chunks.get(0).getContent());
        assertEquals(Map.of("chunk_source", "api"), chunks.get(0).getMetadata());
    }

    @Test
    void ingest_overlappingChunks_shouldAssignIncreasingChunkIndex() {
        when(repository.ensureDocument(eq("kb1"), eq("doc1"))).thenReturn("docId1");

        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);

        CreateKbDocumentRequest req = new CreateKbDocumentRequest();
        req.setKbKey("kb1");
        req.setName("doc1");
        req.setContent("abcdefghijklmnopqrstuvwxyz");
        req.setChunkSize(5);
        req.setOverlap(1);

        service.ingest(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KbChunkAggregate>> chunksCaptor = (ArgumentCaptor<List<KbChunkAggregate>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(repository).saveChunks(eq("docId1"), chunksCaptor.capture());

        List<KbChunkAggregate> chunks = chunksCaptor.getValue();
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getChunkIndex());
            assertNotNull(chunks.get(i).getContent());
            // chunk content is produced by substring, can be < chunkSize for the tail.
            assertTrue(chunks.get(i).getContent().length() <= req.getChunkSize());
        }
    }
}

