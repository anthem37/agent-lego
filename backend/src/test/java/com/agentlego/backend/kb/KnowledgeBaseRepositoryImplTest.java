package com.agentlego.backend.kb;

import com.agentlego.backend.kb.domain.KbChunkAggregate;
import com.agentlego.backend.kb.domain.KnowledgeBaseRepository;
import com.agentlego.backend.kb.infrastructure.KnowledgeBaseRepositoryImpl;
import com.agentlego.backend.kb.infrastructure.persistence.KbBaseDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbBaseMapper;
import com.agentlego.backend.kb.infrastructure.persistence.KbChunkDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbChunkMapper;
import com.agentlego.backend.kb.infrastructure.persistence.KbDocumentDO;
import com.agentlego.backend.kb.infrastructure.persistence.KbDocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    @Test
    void insertBase_shouldInsert() {
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(baseMapper, documentMapper, chunkMapper);
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
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(baseMapper, documentMapper, chunkMapper);
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
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(baseMapper, documentMapper, chunkMapper);
        KbChunkDO row = new KbChunkDO();
        row.setId("c1");
        row.setDocumentId("d1");
        row.setChunkIndex(0);
        row.setContent("hello");
        row.setMetadataJson("{}");
        row.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        row.setDocumentName("n1");
        when(chunkMapper.searchChunks(eq("b1"), eq("q"), eq(5))).thenReturn(List.of(row));
        List<KbChunkAggregate> list = repo.queryChunksByBaseId("b1", "q", 5);
        assertEquals(1, list.size());
        assertEquals("n1", list.get(0).getDocumentName());
    }

    @Test
    void findBaseByKbKey_shouldReturnOptional() {
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(baseMapper, documentMapper, chunkMapper);
        KbBaseDO row = new KbBaseDO();
        row.setId("id1");
        row.setKbKey("k1");
        row.setName("N");
        row.setCreatedAt(Instant.now());
        when(baseMapper.findByKbKey("k1")).thenReturn(row);
        assertTrue(repo.findBaseByKbKey("k1").isPresent());
        assertEquals("id1", repo.findBaseByKbKey("k1").get().getId());
    }
}
