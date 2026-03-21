package com.agentlego.backend.kb.application;

import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.kb.application.dto.KbCollectionDeleteResult;
import com.agentlego.backend.kb.application.mapper.KbDtoMapper;
import com.agentlego.backend.kb.application.service.KbApplicationService;
import com.agentlego.backend.kb.domain.KbChunkRepository;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbCollectionRepository;
import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.support.ModelEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbApplicationServiceDeleteCollectionTest {

    @Mock
    private KbCollectionRepository collectionRepository;
    @Mock
    private KbDocumentRepository documentRepository;
    @Mock
    private KbChunkRepository chunkRepository;
    @Mock
    private ModelRepository modelRepository;
    @Mock
    private ModelEmbeddingClient embeddingClient;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private KbDtoMapper kbDtoMapper;
    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void deleteCollection_stripsKbPolicy_thenDeletes() {
        KbApplicationService svc = new KbApplicationService(
                collectionRepository,
                documentRepository,
                chunkRepository,
                modelRepository,
                embeddingClient,
                agentRepository,
                kbDtoMapper,
                transactionManager,
                524_288,
                2_000
        );

        KbCollectionAggregate col = new KbCollectionAggregate();
        col.setId("c1");
        when(collectionRepository.findById("c1")).thenReturn(Optional.of(col));
        when(agentRepository.listAgentIdsReferencingKbCollection("c1")).thenReturn(List.of("a1"));

        AgentAggregate agent = new AgentAggregate();
        agent.setId("a1");
        LinkedHashMap<String, Object> pol = new LinkedHashMap<>();
        pol.put("collectionIds", List.of("c1", "c2"));
        pol.put("topK", 3);
        agent.setKnowledgeBasePolicy(pol);
        when(agentRepository.findById("a1")).thenReturn(Optional.of(agent));

        KbCollectionDeleteResult r = svc.deleteCollection("c1");

        assertEquals(1, r.agentsPolicyUpdated());

        verify(agentRepository).updateKnowledgeBasePolicy(
                eq("a1"),
                argThat(m -> m != null
                        && List.of("c2").equals(m.get("collectionIds"))
                        && Integer.valueOf(3).equals(m.get("topK")))
        );

        verify(collectionRepository).deleteById("c1");
    }
}
