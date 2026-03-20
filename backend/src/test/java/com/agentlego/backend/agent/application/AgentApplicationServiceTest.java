package com.agentlego.backend.agent.application;

import com.agentlego.backend.agent.application.dto.CreateAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.kb.application.KnowledgeBaseApplicationService;
import com.agentlego.backend.kb.application.dto.KbChunkDto;
import com.agentlego.backend.kb.application.dto.KbQueryResponse;
import com.agentlego.backend.memory.application.MemoryApplicationService;
import com.agentlego.backend.memory.application.dto.MemoryItemDto;
import com.agentlego.backend.memory.application.dto.MemoryQueryRequest;
import com.agentlego.backend.memory.application.dto.MemoryQueryResponse;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.runtime.AgentRuntime;
import com.agentlego.backend.runtime.definition.AgentDefinition;
import com.agentlego.backend.tool.application.ToolExecutionService;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentApplicationService 单元测试。
 * <p>
 * 覆盖点：
 * - createAgent 的默认策略/默认 toolIds 行为
 * - runAgent 的 memory/KB 注入逻辑与开关条件
 */
@ExtendWith(MockitoExtension.class)
class AgentApplicationServiceTest {

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
    private MemoryApplicationService memoryApplicationService;
    @Mock
    private KnowledgeBaseApplicationService knowledgeBaseApplicationService;

    @Test
    void createAgent_shouldDefaultPoliciesAndToolIds() {
        AgentApplicationService service = new AgentApplicationService(
                agentRepository,
                modelRepository,
                toolRepository,
                toolExecutionService,
                agentRuntime,
                memoryApplicationService,
                knowledgeBaseApplicationService
        );

        CreateAgentRequest req = new CreateAgentRequest();
        req.setName("agent1");
        req.setSystemPrompt("SYS");
        req.setModelId("model1");
        req.setToolIds(null);
        req.setMemoryPolicy(null);
        req.setKnowledgeBasePolicy(null);

        ModelAggregate model = new ModelAggregate();
        model.setId("model1");
        when(modelRepository.findById("model1")).thenReturn(Optional.of(model));
        when(agentRepository.save(any())).thenReturn("agent-id-1");

        String id = service.createAgent(req);
        assertEquals("agent-id-1", id);

        ArgumentCaptor<AgentAggregate> captor = ArgumentCaptor.forClass(AgentAggregate.class);
        verify(agentRepository).save(captor.capture());
        AgentAggregate saved = captor.getValue();
        assertNotNull(saved.getToolIds());
        assertTrue(saved.getToolIds().isEmpty());
        assertNotNull(saved.getMemoryPolicy());
        assertTrue(saved.getMemoryPolicy().isEmpty());
        assertNotNull(saved.getKnowledgeBasePolicy());
        assertTrue(saved.getKnowledgeBasePolicy().isEmpty());
        assertEquals("model1", saved.getModelId());
    }

    @Test
    void runAgent_shouldAppendMemoryAndKnowledgeBaseToSystemPrompt() {
        AgentApplicationService service = new AgentApplicationService(
                agentRepository,
                modelRepository,
                toolRepository,
                toolExecutionService,
                agentRuntime,
                memoryApplicationService,
                knowledgeBaseApplicationService
        );

        AgentAggregate agentAgg = new AgentAggregate();
        agentAgg.setId("agent1");
        agentAgg.setName("Agent1");
        agentAgg.setSystemPrompt("SYS");
        agentAgg.setToolIds(List.of("tool1"));
        agentAgg.setMemoryPolicy(Map.of("ownerScope", "user1", "topK", 2));
        agentAgg.setKnowledgeBasePolicy(Map.of("kbKey", "kb1", "topK", 3));
        agentAgg.setCreatedAt(Instant.now());

        when(agentRepository.findById("agent1")).thenReturn(Optional.of(agentAgg));

        ModelAggregate model = new ModelAggregate();
        model.setId("model1");
        model.setProvider("DASHSCOPE");
        model.setModelKey("m");
        model.setApiKeyCipher("k");
        model.setCreatedAt(Instant.now());
        when(modelRepository.findById("modelA")).thenReturn(Optional.of(model));

        ToolAggregate tool = new ToolAggregate();
        tool.setId("tool1");
        tool.setToolType(ToolType.LOCAL);
        tool.setName("echo");

        when(toolRepository.findAll()).thenReturn(List.of(tool));

        when(toolExecutionService.buildToolkitForToolIds(anyList())).thenReturn(new Toolkit());

        MemoryQueryResponse mResp = new MemoryQueryResponse();
        MemoryItemDto item1 = new MemoryItemDto();
        item1.setContent("memA");
        mResp.setItems(List.of(item1));

        when(memoryApplicationService.query(any(MemoryQueryRequest.class))).thenReturn(mResp);

        KbQueryResponse kbResp = new KbQueryResponse();
        KbChunkDto chunk = new KbChunkDto();
        chunk.setContent("kbC");
        kbResp.setChunks(List.of(chunk));
        when(knowledgeBaseApplicationService.query(any())).thenReturn(kbResp);

        when(agentRuntime.call(any(AgentDefinition.class), eq("question"), any(Toolkit.class)))
                .thenReturn(Mono.just(Msg.builder().name("assistant").textContent("final").build()));

        RunAgentRequest req = new RunAgentRequest();
        req.setModelId("modelA");
        req.setOptions(Map.of("temperature", 0.2));
        req.setInput("question");

        RunAgentResponse resp = service.runAgent("agent1", req);
        assertEquals("final", resp.getOutput());

        ArgumentCaptor<AgentDefinition> defCaptor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(agentRuntime).call(defCaptor.capture(), eq("question"), any(Toolkit.class));
        String sysPrompt = defCaptor.getValue().systemPrompt();

        String expectedExtra = "[Memory]\n- memA\n\n[KnowledgeBase]\n- kbC\n";
        assertEquals("SYS\n\n" + expectedExtra, sysPrompt);

        verify(memoryApplicationService, times(1)).query(any(MemoryQueryRequest.class));
        verify(knowledgeBaseApplicationService, times(1)).query(any());
    }

    @Test
    void runAgent_shouldNotQueryMemoryWhenOwnerScopeMissing() {
        AgentApplicationService service = new AgentApplicationService(
                agentRepository,
                modelRepository,
                toolRepository,
                toolExecutionService,
                agentRuntime,
                memoryApplicationService,
                knowledgeBaseApplicationService
        );

        AgentAggregate agentAgg = new AgentAggregate();
        agentAgg.setId("agent1");
        agentAgg.setName("Agent1");
        agentAgg.setSystemPrompt("SYS");
        agentAgg.setToolIds(List.of());
        agentAgg.setMemoryPolicy(Map.of("topK", 2)); // missing ownerScope
        agentAgg.setKnowledgeBasePolicy(null);

        when(agentRepository.findById("agent1")).thenReturn(Optional.of(agentAgg));

        ModelAggregate model = new ModelAggregate();
        model.setProvider("DASHSCOPE");
        model.setModelKey("m");
        model.setApiKeyCipher("k");
        when(modelRepository.findById("modelA")).thenReturn(Optional.of(model));

        when(toolRepository.findAll()).thenReturn(List.of());
        when(toolExecutionService.buildToolkitForToolIds(anyList())).thenReturn(new Toolkit());

        when(agentRuntime.call(any(AgentDefinition.class), eq("question"), any(Toolkit.class)))
                .thenReturn(Mono.just(Msg.builder().name("assistant").textContent("final").build()));

        RunAgentRequest req = new RunAgentRequest();
        req.setModelId("modelA");
        req.setInput("question");

        RunAgentResponse resp = service.runAgent("agent1", req);
        assertEquals("final", resp.getOutput());

        verify(memoryApplicationService, never()).query(any());
        verify(knowledgeBaseApplicationService, never()).query(any());

        ArgumentCaptor<AgentDefinition> defCaptor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(agentRuntime).call(defCaptor.capture(), eq("question"), any(Toolkit.class));
        assertEquals("SYS", defCaptor.getValue().systemPrompt());
    }
}

