package com.agentlego.backend.kb;

import com.agentlego.backend.kb.domain.KbChunkAggregate;
import com.agentlego.backend.kb.domain.KnowledgeBaseRepository;
import com.agentlego.backend.kb.infrastructure.KnowledgeBaseRepositoryImpl;
import com.agentlego.backend.kb.infrastructure.persistence.*;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseRepositoryImplTest {

    @Mock
    private KbBaseMapper baseMapper;

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private KbChunkMapper chunkMapper;

    @Mock
    private ModelEmbeddingClient embeddingClient;

    @Test
    void insertBase_shouldInsert() {
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(baseMapper, documentMapper, chunkMapper, embeddingClient);
        when(baseMapper.insert(any())).thenReturn(1);
        String id = repo.insertBase("k1", "Name", "desc");
        assertNotNull(id);
        ArgumentCaptor<KbBaseDO> c = ArgumentCaptor.forClass(KbBaseDO.class);
        verify(baseMapper).insert(c.capture());
        assertEquals("k1", c.getValue().getKbKey());
        assertEquals("Name", c.getValue().getName());
        assertEquals(id, c.getValue().getId());
    }

    @Test
    void createDocument_shouldUseBaseId() {
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(baseMapper, documentMapper, chunkMapper, embeddingClient);
        when(documentMapper.insert(any())).thenReturn(1);
        String docId = repo.createDocument("base1", "doc1", "body", "markdown", "hybrid");
        assertNotNull(docId);
        ArgumentCaptor<KbDocumentDO> c = ArgumentCaptor.forClass(KbDocumentDO.class);
        verify(documentMapper).insert(c.capture());
        assertEquals("base1", c.getValue().getBaseId());
        assertEquals("doc1", c.getValue().getName());
        assertEquals("body", c.getValue().getContentRich());
        assertEquals("markdown", c.getValue().getContentFormat());
        assertEquals("hybrid", c.getValue().getChunkStrategy());
    }

    @Test
    void queryChunksByBaseId_shouldMap() {
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(baseMapper, documentMapper, chunkMapper, embeddingClient);
        KbChunkDO row = new KbChunkDO();
        row.setId("c1");
        row.setDocumentId("d1");
        row.setChunkIndex(0);
        row.setContent("hello");
        row.setMetadataJson("{}");
        row.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        row.setDocumentName("n1");
        when(chunkMapper.searchChunks(eq("b1"), eq("q"), eq(5))).thenReturn(List.of(row));
        List<KbChunkAggregate> list = repo.queryChunksByBaseId("b1", "q", 5, null);
        assertEquals(1, list.size());
        assertEquals("n1", list.get(0).getDocumentName());
    }

    @Test
    void findBaseByKbKey_shouldReturnOptional() {
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(baseMapper, documentMapper, chunkMapper, embeddingClient);
        KbBaseDO row = new KbBaseDO();
        row.setId("id1");
        row.setKbKey("k1");
        row.setName("N");
        row.setCreatedAt(Instant.now());
        when(baseMapper.findByKbKey("k1")).thenReturn(row);
        assertTrue(repo.findBaseByKbKey("k1").isPresent());
        assertEquals("id1", repo.findBaseByKbKey("k1").get().getId());
    }

    @Test
    void queryChunksByBaseId_vector_shouldRankByCosine() {
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(baseMapper, documentMapper, chunkMapper, embeddingClient);

        KbChunkDO c1 = new KbChunkDO();
        c1.setId("c1");
        c1.setDocumentId("d1");
        c1.setChunkIndex(0);
        c1.setContent("a");
        c1.setMetadataJson("{}");
        c1.setEmbeddingJson(null);
        c1.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        c1.setDocumentName("doc1");

        KbChunkDO c2 = new KbChunkDO();
        c2.setId("c2");
        c2.setDocumentId("d1");
        c2.setChunkIndex(1);
        c2.setContent("b");
        c2.setMetadataJson("{}");
        c2.setEmbeddingJson(null);
        c2.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        c2.setDocumentName("doc1");

        when(chunkMapper.listChunksByBaseIdWithEmbedding(eq("b1"))).thenReturn(List.of(c1, c2));

        // query 向量：[1, 0]
        when(embeddingClient.embed(eq("m1"), eq(List.of("hi"))))
                .thenReturn(List.of(new float[]{1f, 0f}));

        // chunks 向量：a -> [1,0] score 1; b -> [0,1] score 0
        when(embeddingClient.embed(eq("m1"), eq(List.of("a", "b"))))
                .thenReturn(List.of(new float[]{1f, 0f}, new float[]{0f, 1f}));

        when(chunkMapper.updateChunkEmbedding(any(), any(), any())).thenReturn(1);

        List<KbChunkAggregate> list = repo.queryChunksByBaseId("b1", "hi", 1, "m1");
        assertEquals(1, list.size());
        assertEquals("c1", list.get(0).getId());
    }
}
