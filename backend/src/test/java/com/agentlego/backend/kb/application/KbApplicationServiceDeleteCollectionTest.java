package com.agentlego.backend.kb.application;

import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.kb.application.dto.KbCollectionDeleteResult;
import com.agentlego.backend.kb.application.mapper.KbDtoMapper;
import com.agentlego.backend.kb.application.service.*;
import com.agentlego.backend.kb.application.validation.KbDocumentValidator;
import com.agentlego.backend.kb.domain.KbCollectionAggregate;
import com.agentlego.backend.kb.domain.KbCollectionRepository;
import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.kb.support.KbRetrievePreviewAssembler;
import com.agentlego.backend.kb.vector.KbVectorStore;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private KbVectorStore vectorStore;
    @Mock
    private KbCollectionCommandService kbCollectionCommandService;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private ToolRepository toolRepository;
    @Mock
    private KbDtoMapper kbDtoMapper;
    @Mock
    private KbDocumentValidator documentValidator;
    @Mock
    private KbRetrievePreviewAssembler retrievePreviewAssembler;
    @Mock
    private KbVectorRetrieveRunner vectorRetrieveRunner;
    @Mock
    private KbIngestPayloadPreparer ingestPayloadPreparer;
    @Mock
    private KbIngestFinalizeRunner ingestFinalizeRunner;
    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    void deleteCollection_stripsKbPolicy_thenDeletes_thenDropsVectorStore() {
        KbCollectionAccess kbCollectionAccess = new KbCollectionAccess(collectionRepository, documentRepository);
        KbApplicationService svc = new KbApplicationService(
                collectionRepository,
                documentRepository,
                vectorStore,
                kbCollectionCommandService,
                new ObjectMapper(),
                agentRepository,
                toolRepository,
                kbDtoMapper,
                documentValidator,
                retrievePreviewAssembler,
                vectorRetrieveRunner,
                kbCollectionAccess,
                ingestPayloadPreparer,
                ingestFinalizeRunner,
                transactionManager
        );

        KbCollectionAggregate col = new KbCollectionAggregate();
        col.setId("c1");
        col.setVectorStoreConfigJson("{\"host\":\"h\",\"collectionName\":\"coll\"}");
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
        verify(vectorStore).dropPhysicalCollection(col);
    }
}
