package com.agentlego.backend.agent.application;

import com.agentlego.backend.agent.application.dto.CreateAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.application.mapper.AgentDtoMapper;
import com.agentlego.backend.agent.application.service.AgentApplicationService;
import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.kb.rag.KbRagKnowledgeFactory;
import com.agentlego.backend.memorypolicy.domain.MemoryItemRepository;
import com.agentlego.backend.memorypolicy.domain.MemoryPolicyRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import com.agentlego.backend.memorypolicy.runtime.MemoryVectorIndexService;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.runtime.AgentRuntime;
import com.agentlego.backend.runtime.definition.AgentDefinition;
import com.agentlego.backend.tool.application.service.ToolExecutionService;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentApplicationServiceTest {

    private static final AgentDtoMapper AGENT_DTO_MAPPER = Mappers.getMapper(AgentDtoMapper.class);
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private ModelRepository modelRepository;
    @Mock
    private ToolRepository toolRepository;
    @Mock
    private ToolExecutionService toolExecutionService;
    @Mock
    private AgentRuntime agentRuntime;
    @Mock
    private KbRagKnowledgeFactory kbRagKnowledgeFactory;
    @Mock
    private MemoryPolicyRepository memoryPolicyRepository;
    @Mock
    private MemoryItemRepository memoryItemRepository;
    @Mock
    private MemoryVectorIndexService memoryVectorIndexService;

    private AgentApplicationService newAgentApplicationService() {
        return new AgentApplicationService(
                agentRepository,
                modelRepository,
                toolRepository,
                toolExecutionService,
                agentRuntime,
                kbRagKnowledgeFactory,
                memoryPolicyRepository,
                memoryItemRepository,
                memoryVectorIndexService,
                AGENT_DTO_MAPPER
        );
    }

    @Test
    void createAgent_shouldDefaultPoliciesAndToolIds() {
        AgentApplicationService service = newAgentApplicationService();

        CreateAgentRequest req = new CreateAgentRequest();
        req.setName("agent1");
        req.setSystemPrompt("SYS");
        req.setModelId("model1");
        req.setToolIds(null);

        ModelAggregate model = new ModelAggregate();
        model.setId("model1");
        model.setProvider("DASHSCOPE");
        when(modelRepository.findById("model1")).thenReturn(Optional.of(model));
        when(agentRepository.save(any())).thenReturn("agent-id-1");

        String id = service.createAgent(req);
        assertEquals("agent-id-1", id);

        ArgumentCaptor<AgentAggregate> captor = ArgumentCaptor.forClass(AgentAggregate.class);
        verify(agentRepository).save(captor.capture());
        AgentAggregate saved = captor.getValue();
        assertNotNull(saved.getToolIds());
        assertTrue(saved.getToolIds().isEmpty());
        assertNotNull(saved.getKnowledgeBasePolicy());
        assertTrue(saved.getKnowledgeBasePolicy().isEmpty());
        assertNull(saved.getMemoryPolicyId());
        assertEquals("model1", saved.getModelId());
    }

    @Test
    void runAgent_shouldBuildDefinitionWithoutLongTermMemory() {
        AgentApplicationService service = newAgentApplicationService();
        when(kbRagKnowledgeFactory.resolve(any(AgentAggregate.class), any())).thenReturn(Optional.empty());

        AgentAggregate agentAgg = new AgentAggregate();
        agentAgg.setId("agent1");
        agentAgg.setName("Agent1");
        agentAgg.setSystemPrompt("SYS");
        agentAgg.setToolIds(List.of("tool1"));
        agentAgg.setCreatedAt(Instant.now());

        when(agentRepository.findById("agent1")).thenReturn(Optional.of(agentAgg));

        ModelAggregate model = new ModelAggregate();
        model.setId("modelA");
        model.setProvider("DASHSCOPE");
        model.setModelKey("m");
        model.setApiKeyCipher("k");
        model.setCreatedAt(Instant.now());
        when(modelRepository.findById("modelA")).thenReturn(Optional.of(model));

        ToolAggregate tool = new ToolAggregate();
        tool.setId("tool1");
        tool.setToolType(ToolType.LOCAL);
        tool.setName("agent_tool");

        when(toolRepository.findAll()).thenReturn(List.of(tool));

        when(toolExecutionService.buildToolkitForToolIds(anyList())).thenReturn(new Toolkit());

        when(agentRuntime.call(any(AgentDefinition.class), eq("question"), any(Toolkit.class)))
                .thenReturn(Mono.just(Msg.builder().name("assistant").textContent("final").build()));

        RunAgentRequest req = new RunAgentRequest();
        req.setModelId("modelA");
        req.setOptions(Map.of("temperature", 0.2));
        req.setInput("question");

        RunAgentResponse resp = service.runAgent("agent1", req);
        assertEquals("final", resp.getOutput());
        assertNull(resp.getMemory());

        ArgumentCaptor<AgentDefinition> defCaptor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(agentRuntime).call(defCaptor.capture(), eq("question"), any(Toolkit.class));
        AgentDefinition def = defCaptor.getValue();

        assertEquals("SYS", def.systemPrompt());
        assertNull(def.longTermMemory());
        assertNull(def.knowledge());
    }

    @Test
    void runAgent_withMemoryPolicyId_shouldInjectLongTermMemory() {
        AgentApplicationService service = newAgentApplicationService();
        when(kbRagKnowledgeFactory.resolve(any(AgentAggregate.class), any())).thenReturn(Optional.empty());

        AgentAggregate agentAgg = new AgentAggregate();
        agentAgg.setId("agent1");
        agentAgg.setName("Agent1");
        agentAgg.setSystemPrompt("SYS");
        agentAgg.setToolIds(List.of("tool1"));
        agentAgg.setMemoryPolicyId("pol1");
        agentAgg.setCreatedAt(Instant.now());

        MemoryPolicyDO pol = new MemoryPolicyDO();
        pol.setId("pol1");
        pol.setName("策略一");
        pol.setOwnerScope("scope-a");
        pol.setStrategyKind("EPISODIC_DIALOGUE");
        pol.setTopK(3);
        pol.setRetrievalMode("KEYWORD");
        pol.setWriteMode("OFF");
        pol.setWriteBackOnDuplicate("skip");
        when(memoryPolicyRepository.findById("pol1")).thenReturn(Optional.of(pol));
        when(memoryItemRepository.searchByKeyword(eq("pol1"), eq("question"), eq(3), isNull(), eq("EPISODIC_DIALOGUE"), eq(true)))
                .thenReturn(List.of());

        when(agentRepository.findById("agent1")).thenReturn(Optional.of(agentAgg));

        ModelAggregate model = new ModelAggregate();
        model.setId("modelA");
        model.setProvider("DASHSCOPE");
        model.setModelKey("m");
        model.setApiKeyCipher("k");
        model.setCreatedAt(Instant.now());
        when(modelRepository.findById("modelA")).thenReturn(Optional.of(model));

        ToolAggregate tool = new ToolAggregate();
        tool.setId("tool1");
        tool.setToolType(ToolType.LOCAL);
        tool.setName("agent_tool");

        when(toolRepository.findAll()).thenReturn(List.of(tool));

        when(toolExecutionService.buildToolkitForToolIds(anyList())).thenReturn(new Toolkit());

        when(agentRuntime.call(any(AgentDefinition.class), eq("question"), any(Toolkit.class)))
                .thenReturn(Mono.just(Msg.builder().name("assistant").textContent("final").build()));

        RunAgentRequest req = new RunAgentRequest();
        req.setModelId("modelA");
        req.setInput("question");

        RunAgentResponse resp = service.runAgent("agent1", req);
        assertEquals("final", resp.getOutput());

        ArgumentCaptor<AgentDefinition> defCaptor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(agentRuntime).call(defCaptor.capture(), eq("question"), any(Toolkit.class));
        AgentDefinition def = defCaptor.getValue();

        assertNotNull(def.longTermMemory());
        assertNull(def.knowledge());
        assertNotNull(resp.getMemory());
        assertEquals("pol1", resp.getMemory().getMemoryPolicyId());
        assertEquals("策略一", resp.getMemory().getMemoryPolicyName());
        assertEquals(0, resp.getMemory().getPreviewHitCount().intValue());
        assertEquals(480, resp.getMemory().getRoughSummaryMaxCharsResolved().intValue());
    }

    @Test
    void runAgent_withMemoryPolicyId_shouldIncludePreviewHits() {
        AgentApplicationService service = newAgentApplicationService();
        when(kbRagKnowledgeFactory.resolve(any(AgentAggregate.class), any())).thenReturn(Optional.empty());

        AgentAggregate agentAgg = new AgentAggregate();
        agentAgg.setId("agent1");
        agentAgg.setName("Agent1");
        agentAgg.setSystemPrompt("SYS");
        agentAgg.setToolIds(List.of("tool1"));
        agentAgg.setMemoryPolicyId("pol1");
        agentAgg.setCreatedAt(Instant.now());

        MemoryPolicyDO pol = new MemoryPolicyDO();
        pol.setId("pol1");
        pol.setName("P");
        pol.setOwnerScope("scope-a");
        pol.setStrategyKind("EPISODIC_DIALOGUE");
        pol.setTopK(3);
        pol.setRetrievalMode("KEYWORD");
        pol.setWriteMode("OFF");
        pol.setWriteBackOnDuplicate("skip");
        when(memoryPolicyRepository.findById("pol1")).thenReturn(Optional.of(pol));

        MemoryItemDO hit = new MemoryItemDO();
        hit.setContent("hello memory");
        when(memoryItemRepository.searchByKeyword(eq("pol1"), eq("q1"), eq(3), isNull(), eq("EPISODIC_DIALOGUE"), eq(true)))
                .thenReturn(List.of(hit));

        when(agentRepository.findById("agent1")).thenReturn(Optional.of(agentAgg));

        ModelAggregate model = new ModelAggregate();
        model.setId("modelA");
        model.setProvider("DASHSCOPE");
        model.setModelKey("m");
        model.setApiKeyCipher("k");
        model.setCreatedAt(Instant.now());
        when(modelRepository.findById("modelA")).thenReturn(Optional.of(model));

        ToolAggregate tool = new ToolAggregate();
        tool.setId("tool1");
        tool.setToolType(ToolType.LOCAL);
        tool.setName("agent_tool");

        when(toolRepository.findAll()).thenReturn(List.of(tool));

        when(toolExecutionService.buildToolkitForToolIds(anyList())).thenReturn(new Toolkit());

        when(agentRuntime.call(any(AgentDefinition.class), eq("q1"), any(Toolkit.class)))
                .thenReturn(Mono.just(Msg.builder().name("assistant").textContent("final").build()));

        RunAgentRequest req = new RunAgentRequest();
        req.setModelId("modelA");
        req.setInput("q1");

        RunAgentResponse resp = service.runAgent("agent1", req);
        assertEquals(1, resp.getMemory().getPreviewHitCount().intValue());
        assertTrue(resp.getMemory().getPreviewText().contains("hello memory"));
    }

    @Test
    void runAgent_withMemoryNamespace_shouldPassToKeywordPreview() {
        AgentApplicationService service = newAgentApplicationService();
        when(kbRagKnowledgeFactory.resolve(any(AgentAggregate.class), any())).thenReturn(Optional.empty());

        AgentAggregate agentAgg = new AgentAggregate();
        agentAgg.setId("agent1");
        agentAgg.setName("Agent1");
        agentAgg.setSystemPrompt("SYS");
        agentAgg.setToolIds(List.of("tool1"));
        agentAgg.setMemoryPolicyId("pol1");
        agentAgg.setCreatedAt(Instant.now());

        MemoryPolicyDO pol = new MemoryPolicyDO();
        pol.setId("pol1");
        pol.setName("P");
        pol.setOwnerScope("scope-a");
        pol.setStrategyKind("EPISODIC_DIALOGUE");
        pol.setTopK(3);
        pol.setRetrievalMode("KEYWORD");
        pol.setWriteMode("OFF");
        pol.setWriteBackOnDuplicate("skip");
        when(memoryPolicyRepository.findById("pol1")).thenReturn(Optional.of(pol));

        when(memoryItemRepository.searchByKeyword(eq("pol1"), eq("hi"), eq(3), eq("user-42"), eq("EPISODIC_DIALOGUE"), eq(true)))
                .thenReturn(List.of());

        when(agentRepository.findById("agent1")).thenReturn(Optional.of(agentAgg));

        ModelAggregate model = new ModelAggregate();
        model.setId("modelA");
        model.setProvider("DASHSCOPE");
        model.setModelKey("m");
        model.setApiKeyCipher("k");
        model.setCreatedAt(Instant.now());
        when(modelRepository.findById("modelA")).thenReturn(Optional.of(model));

        ToolAggregate tool = new ToolAggregate();
        tool.setId("tool1");
        tool.setToolType(ToolType.LOCAL);
        tool.setName("agent_tool");

        when(toolRepository.findAll()).thenReturn(List.of(tool));

        when(toolExecutionService.buildToolkitForToolIds(anyList())).thenReturn(new Toolkit());

        when(agentRuntime.call(any(AgentDefinition.class), eq("hi"), any(Toolkit.class)))
                .thenReturn(Mono.just(Msg.builder().name("assistant").textContent("final").build()));

        RunAgentRequest req = new RunAgentRequest();
        req.setModelId("modelA");
        req.setInput("hi");
        req.setMemoryNamespace("user-42");

        RunAgentResponse resp = service.runAgent("agent1", req);
        assertEquals("user-42", resp.getMemory().getMemoryNamespace());
        verify(memoryItemRepository).searchByKeyword(eq("pol1"), eq("hi"), eq(3), eq("user-42"), eq("EPISODIC_DIALOGUE"), eq(true));
    }

    @Test
    void createAgent_withEmbeddingModel_shouldReject() {
        AgentApplicationService service = newAgentApplicationService();

        CreateAgentRequest req = new CreateAgentRequest();
        req.setName("a");
        req.setSystemPrompt("SYS");
        req.setModelId("emb1");

        ModelAggregate emb = new ModelAggregate();
        emb.setId("emb1");
        emb.setProvider("OPENAI_TEXT_EMBEDDING");
        when(modelRepository.findById("emb1")).thenReturn(Optional.of(emb));

        ApiException ex = assertThrows(ApiException.class, () -> service.createAgent(req));
        assertEquals("INVALID_AGENT_MODEL", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(agentRepository, never()).save(any());
    }
}
