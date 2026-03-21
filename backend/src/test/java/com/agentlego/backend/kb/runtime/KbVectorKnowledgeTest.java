package com.agentlego.backend.kb.runtime;

import com.agentlego.backend.kb.domain.KbChunkHit;
import com.agentlego.backend.kb.domain.KbChunkRepository;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KbVectorKnowledgeTest {

    @Mock
    private KbChunkRepository chunkRepository;
    @Mock
    private ModelEmbeddingClient embeddingClient;

    @Test
    void retrieve_blankQuery_returnsEmpty() {
        KbVectorKnowledge k = new KbVectorKnowledge(List.of("c1"), "emb1", chunkRepository, embeddingClient, 50, true);
        List<Document> out = k.retrieve("   ", RetrieveConfig.builder().limit(3).build())
                .block(Duration.ofSeconds(2));
        assertThat(out).isEmpty();
    }

    @Test
    void retrieve_mergesVectorAndKeywordChannels() {
        when(embeddingClient.embed(eq("emb1"), any())).thenReturn(List.of(new float[]{0.1f, 0.2f, 0.3f}));

        KbChunkHit vecOnly = new KbChunkHit();
        vecOnly.setId("chunk-vec");
        vecOnly.setDocumentId("doc1");
        vecOnly.setContent("vector text");
        vecOnly.setSimilarity(0.9);

        KbChunkHit kwOnly = new KbChunkHit();
        kwOnly.setId("chunk-kw");
        kwOnly.setDocumentId("doc2");
        kwOnly.setContent("keyword text");
        kwOnly.setSimilarity(0.04);

        when(chunkRepository.searchByCosineSimilarity(eq(List.of("c1")), any(float[].class), anyInt()))
                .thenReturn(List.of(vecOnly));
        when(chunkRepository.searchByFullText(eq(List.of("c1")), eq("q"), anyInt()))
                .thenReturn(List.of(kwOnly));

        KbVectorKnowledge k = new KbVectorKnowledge(List.of("c1"), "emb1", chunkRepository, embeddingClient, 50, true);
        List<Document> out = k.retrieve(
                "q",
                RetrieveConfig.builder().limit(10).scoreThreshold(0.25).build()
        ).block(Duration.ofSeconds(2));

        assertThat(out).hasSize(2);
        assertThat(out.stream().map(d -> d.getMetadata().getChunkId()).toList())
                .containsExactlyInAnyOrder("chunk-vec", "chunk-kw");
    }

    @Test
    void retrieve_belowThresholdFiltered() {
        when(embeddingClient.embed(eq("emb1"), any())).thenReturn(List.of(new float[]{1f}));

        KbChunkHit weak = new KbChunkHit();
        weak.setId("w1");
        weak.setDocumentId("d");
        weak.setContent("x");
        weak.setSimilarity(0.1);

        when(chunkRepository.searchByCosineSimilarity(any(), any(), anyInt())).thenReturn(List.of(weak));
        when(chunkRepository.searchByFullText(any(), any(), anyInt())).thenReturn(List.of());

        KbVectorKnowledge k = new KbVectorKnowledge(List.of("c1"), "emb1", chunkRepository, embeddingClient, 50, true);
        List<Document> out = k.retrieve(
                "query",
                RetrieveConfig.builder().limit(5).scoreThreshold(0.25).build()
        ).block(Duration.ofSeconds(2));

        assertThat(out).isEmpty();
    }

    @Test
    void retrieve_fullTextDisabled_skipsFullTextRepository() {
        when(embeddingClient.embed(eq("emb1"), any())).thenReturn(List.of(new float[]{1f}));
        KbChunkHit vec = new KbChunkHit();
        vec.setId("c1");
        vec.setDocumentId("d1");
        vec.setContent("only vector");
        vec.setSimilarity(0.8);
        when(chunkRepository.searchByCosineSimilarity(any(), any(), anyInt())).thenReturn(List.of(vec));

        KbVectorKnowledge k = new KbVectorKnowledge(List.of("c1"), "emb1", chunkRepository, embeddingClient, 50, false);
        List<Document> out = k.retrieve("any query", RetrieveConfig.builder().limit(3).build())
                .block(Duration.ofSeconds(2));

        assertThat(out).hasSize(1);
        verify(chunkRepository, never()).searchByFullText(any(), any(), anyInt());
    }
}
