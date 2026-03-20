package com.agentlego.backend.kb;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.application.KnowledgeBaseApplicationService;
import com.agentlego.backend.kb.application.dto.CreateKnowledgeRequest;
import com.agentlego.backend.kb.application.dto.KbIngestResponse;
import com.agentlego.backend.kb.application.dto.KbQueryRequest;
import com.agentlego.backend.kb.domain.KbBaseSummary;
import com.agentlego.backend.kb.domain.KbChunkAggregate;
import com.agentlego.backend.kb.domain.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseApplicationServiceTest {

    @Mock
    private KnowledgeBaseRepository repository;

    @Test
    void ingestKnowledge_chunkInvalid_shouldThrow() {
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);
        when(repository.findBaseById("b1")).thenReturn(Optional.of(new KbBaseSummary()));

        CreateKnowledgeRequest req = new CreateKnowledgeRequest();
        req.setName("n");
        req.setContent("a");
        req.setChunkSize(5);
        req.setOverlap(5);

        assertThrows(ApiException.class, () -> service.ingestKnowledge("b1", req));
    }

    @Test
    void ingestKnowledge_ok_shouldSaveChunks() {
        when(repository.findBaseById("b1")).thenReturn(Optional.of(new KbBaseSummary()));
        when(repository.createDocument(eq("b1"), eq("doc1"), eq("abc"), eq("markdown"), eq("fixed"))).thenReturn("docId1");

        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);

        CreateKnowledgeRequest req = new CreateKnowledgeRequest();
        req.setName("doc1");
        req.setContent("abc");
        req.setChunkSize(10);
        req.setOverlap(1);

        KbIngestResponse resp = service.ingestKnowledge("b1", req);
        assertEquals("docId1", resp.getDocumentId());
        assertEquals(1, resp.getChunkCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KbChunkAggregate>> cap = ArgumentCaptor.forClass(List.class);
        verify(repository).saveChunks(eq("docId1"), cap.capture());
        assertEquals(1, cap.getValue().size());
        assertEquals(
                Map.of("chunk_source", "api", "content_format", "markdown", "chunk_strategy", "fixed"),
                cap.getValue().get(0).getMetadata());
    }

    @Test
    void ingestKnowledge_plainFormat_shouldReject() {
        when(repository.findBaseById("b1")).thenReturn(Optional.of(new KbBaseSummary()));
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);
        CreateKnowledgeRequest req = new CreateKnowledgeRequest();
        req.setName("n");
        req.setContent("x");
        req.setContentFormat("plain");
        req.setChunkSize(200);
        req.setOverlap(10);
        ApiException ex = assertThrows(ApiException.class, () -> service.ingestKnowledge("b1", req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
        verify(repository, never()).createDocument(any(), any(), any(), any(), any());
    }

    @Test
    void ingestKnowledge_markdown_ok_shouldPlainForChunks() {
        when(repository.findBaseById("b1")).thenReturn(Optional.of(new KbBaseSummary()));
        when(repository.createDocument(eq("b1"), eq("t"), eq("# H\n\nbody"), eq("markdown"), eq("fixed"))).thenReturn("docMd");

        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);

        CreateKnowledgeRequest req = new CreateKnowledgeRequest();
        req.setName("t");
        req.setContent("# H\n\nbody");
        req.setContentFormat("markdown");
        req.setChunkSize(50);
        req.setOverlap(1);

        KbIngestResponse resp = service.ingestKnowledge("b1", req);
        assertEquals("docMd", resp.getDocumentId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KbChunkAggregate>> cap = ArgumentCaptor.forClass(List.class);
        verify(repository).saveChunks(eq("docMd"), cap.capture());
        assertEquals(1, cap.getValue().size());
        String chunk = cap.getValue().get(0).getContent();
        assertTrue(chunk.contains("H"));
        assertTrue(chunk.contains("body"));
        assertEquals("markdown", cap.getValue().get(0).getMetadata().get("content_format"));
        assertEquals("fixed", cap.getValue().get(0).getMetadata().get("chunk_strategy"));
    }

    @Test
    void ingestKnowledge_html_ok_shouldStripForChunks() {
        when(repository.findBaseById("b1")).thenReturn(Optional.of(new KbBaseSummary()));
        when(repository.createDocument(eq("b1"), eq("t"), eq("<p>a</p>"), eq("html"), eq("fixed"))).thenReturn("docHtml");

        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);

        CreateKnowledgeRequest req = new CreateKnowledgeRequest();
        req.setName("t");
        req.setContent("<p>a</p>");
        req.setContentFormat("html");
        req.setChunkSize(10);
        req.setOverlap(1);

        KbIngestResponse resp = service.ingestKnowledge("b1", req);
        assertEquals("docHtml", resp.getDocumentId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KbChunkAggregate>> cap = ArgumentCaptor.forClass(List.class);
        verify(repository).saveChunks(eq("docHtml"), cap.capture());
        assertEquals(1, cap.getValue().size());
        assertEquals("a", cap.getValue().get(0).getContent());
        assertEquals("html", cap.getValue().get(0).getMetadata().get("content_format"));
        assertEquals("fixed", cap.getValue().get(0).getMetadata().get("chunk_strategy"));
    }

    @Test
    void ingestKnowledge_unknownChunkStrategy_shouldThrow() {
        when(repository.findBaseById("b1")).thenReturn(Optional.of(new KbBaseSummary()));
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);
        CreateKnowledgeRequest req = new CreateKnowledgeRequest();
        req.setName("n");
        req.setContent("hello world here");
        req.setChunkStrategy("nope");
        req.setChunkSize(200);
        req.setOverlap(10);
        ApiException ex = assertThrows(ApiException.class, () -> service.ingestKnowledge("b1", req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
        verify(repository, never()).createDocument(any(), any(), any(), any(), any());
    }

    @Test
    void ingestKnowledge_paragraphStrategy_shouldPersistStrategy() {
        when(repository.findBaseById("b1")).thenReturn(Optional.of(new KbBaseSummary()));
        when(repository.createDocument(eq("b1"), eq("t"), eq("a\n\nb"), eq("markdown"), eq("paragraph"))).thenReturn("docP");

        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);
        CreateKnowledgeRequest req = new CreateKnowledgeRequest();
        req.setName("t");
        req.setContent("a\n\nb");
        req.setChunkStrategy("paragraph");
        req.setChunkSize(200);
        req.setOverlap(10);

        service.ingestKnowledge("b1", req);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KbChunkAggregate>> cap = ArgumentCaptor.forClass(List.class);
        verify(repository).saveChunks(eq("docP"), cap.capture());
        assertEquals("paragraph", cap.getValue().get(0).getMetadata().get("chunk_strategy"));
    }

    @Test
    void ingestKnowledge_htmlEmpty_shouldThrow() {
        when(repository.findBaseById("b1")).thenReturn(Optional.of(new KbBaseSummary()));
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);
        CreateKnowledgeRequest req = new CreateKnowledgeRequest();
        req.setName("t");
        req.setContent("<p><br></p>");
        req.setContentFormat("html");
        req.setChunkSize(200);
        req.setOverlap(10);
        assertThrows(ApiException.class, () -> service.ingestKnowledge("b1", req));
        verify(repository, never()).createDocument(any(), any(), any(), any(), any());
    }

    @Test
    void query_missingBaseAndKey_shouldThrow() {
        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);
        KbQueryRequest req = new KbQueryRequest();
        req.setQueryText("x");
        ApiException ex = assertThrows(ApiException.class, () -> service.query(req));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void query_withKbKey_shouldResolveBase() {
        KbBaseSummary b = new KbBaseSummary();
        b.setId("bid");
        when(repository.findBaseByKbKey("k1")).thenReturn(Optional.of(b));
        when(repository.queryChunksByBaseId(eq("bid"), eq("hi"), eq(3))).thenReturn(List.of());

        KnowledgeBaseApplicationService service = new KnowledgeBaseApplicationService(repository);
        KbQueryRequest req = new KbQueryRequest();
        req.setKbKey("k1");
        req.setQueryText("hi");
        req.setTopK(3);
        service.query(req);
        verify(repository).queryChunksByBaseId("bid", "hi", 3);
    }
}
