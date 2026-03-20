package com.agentlego.backend.kb;

import com.agentlego.backend.kb.domain.KbChunkAggregate;
import com.agentlego.backend.kb.domain.KnowledgeBaseRepository;
import com.agentlego.backend.kb.infrastructure.KnowledgeBaseRepositoryImpl;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * KnowledgeBaseRepositoryImpl 单元测试。
 * <p>
 * 说明：不依赖真实数据库，使用 Mock 的 MyBatis Mapper 验证 DO/Aggregate 映射与分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseRepositoryImplTest {

    @Mock
    private KbDocumentMapper documentMapper;

    @Mock
    private KbChunkMapper chunkMapper;

    @Test
    void ensureDocument_existingDoc_shouldReturnExistingId() {
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(documentMapper, chunkMapper);

        KbDocumentDO existing = new KbDocumentDO();
        existing.setId("doc-existing");
        existing.setKbKey("kb1");
        existing.setName("name1");

        when(documentMapper.findByKbKey("kb1")).thenReturn(existing);

        String id = repo.ensureDocument("kb1", "name1");
        assertEquals("doc-existing", id);
        verify(documentMapper, never()).insert(any());
    }

    @Test
    void ensureDocument_missingDoc_shouldInsertWithGeneratedId() {
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(documentMapper, chunkMapper);

        when(documentMapper.findByKbKey("kb1")).thenReturn(null);
        when(documentMapper.insert(any())).thenReturn(1);

        String id = repo.ensureDocument("kb1", "doc1");
        assertNotNull(id);
        assertFalse(id.isBlank());

        ArgumentCaptor<KbDocumentDO> captor = ArgumentCaptor.forClass(KbDocumentDO.class);
        verify(documentMapper).insert(captor.capture());

        assertEquals("kb1", captor.getValue().getKbKey());
        assertEquals("doc1", captor.getValue().getName());
        assertNotNull(captor.getValue().getId());
        assertFalse(captor.getValue().getId().isBlank());
        // createdAt is set by DB default in real integration; in unit test the DO is not populated.
    }

    @Test
    void queryChunks_shouldMapMetadataJsonToMap() {
        KnowledgeBaseRepository repo = new KnowledgeBaseRepositoryImpl(documentMapper, chunkMapper);

        KbChunkDO row = new KbChunkDO();
        row.setId("c1");
        row.setDocumentId("d1");
        row.setChunkIndex(0);
        row.setContent("hello chunk");
        row.setMetadataJson("{\"a\":1,\"b\":\"x\"}");
        row.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));

        when(chunkMapper.searchChunks("kb1", "q", 5)).thenReturn(List.of(row));

        var results = repo.queryChunks("kb1", "q", 5);
        assertEquals(1, results.size());
        KbChunkAggregate chunk = results.get(0);
        assertEquals("c1", chunk.getId());
        assertEquals(0, chunk.getChunkIndex());
        assertEquals("hello chunk", chunk.getContent());
        assertEquals(1, chunk.getMetadata().get("a"));
        assertEquals("x", chunk.getMetadata().get("b"));
    }
}

