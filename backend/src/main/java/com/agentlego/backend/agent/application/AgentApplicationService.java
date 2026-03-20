package com.agentlego.backend.agent.application;

import com.agentlego.backend.agent.application.dto.AgentDto;
import com.agentlego.backend.agent.application.dto.CreateAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.application.KnowledgeBaseApplicationService;
import com.agentlego.backend.kb.rag.AgentLegoKnowledge;
import com.agentlego.backend.memory.application.MemoryApplicationService;
import com.agentlego.backend.memory.application.dto.MemoryQueryRequest;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.model.support.ModelConfigSummaries;
import com.agentlego.backend.runtime.AgentRuntime;
import com.agentlego.backend.runtime.definition.AgentDefinition;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import com.agentlego.backend.tool.application.ToolExecutionService;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AgentApplicationService {
    private static final String POLICY_OWNER_SCOPE = "ownerScope";
    private static final String POLICY_TOP_K = "topK";
    private static final String POLICY_KB_KEY = "kbKey";
    private static final String POLICY_EMBEDDING_MODEL_ID = "embeddingModelId";

    private final AgentRepository agentRepository;
    private final ModelRepository modelRepository;
    private final ToolRepository toolRepository;
    private final ToolExecutionService toolExecutionService;
    private final AgentRuntime agentRuntime;
    private final MemoryApplicationService memoryApplicationService;
    private final KnowledgeBaseApplicationService knowledgeBaseApplicationService;

    public AgentApplicationService(
            AgentRepository agentRepository,
            ModelRepository modelRepository,
            ToolRepository toolRepository,
            ToolExecutionService toolExecutionService,
            AgentRuntime agentRuntime,
            MemoryApplicationService memoryApplicationService,
            KnowledgeBaseApplicationService knowledgeBaseApplicationService
    ) {
        this.agentRepository = agentRepository;
        this.modelRepository = modelRepository;
        this.toolRepository = toolRepository;
        this.toolExecutionService = toolExecutionService;
        this.agentRuntime = agentRuntime;
        this.memoryApplicationService = memoryApplicationService;
        this.knowledgeBaseApplicationService = knowledgeBaseApplicationService;
    }

    private static int getKbTopK(AgentAggregate agentAgg) {
        Map<String, Object> p = agentAgg.getKnowledgeBasePolicy() == null ? Map.of() : agentAgg.getKnowledgeBasePolicy();
        return JsonMaps.getInt(p, POLICY_TOP_K, 3);
    }

    private static double getKbScoreThreshold(AgentAggregate agentAgg) {
        Map<String, Object> p = agentAgg.getKnowledgeBasePolicy() == null ? Map.of() : agentAgg.getKnowledgeBasePolicy();
        return JsonMaps.getDouble(p, "scoreThreshold", 0.3);
    }

    public String createAgent(CreateAgentRequest req) {
        if (req.getToolIds() == null) {
            req.setToolIds(Collections.emptyList());
        }
        modelRepository.findById(req.getModelId())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "model not found", HttpStatus.NOT_FOUND));
        AgentAggregate agg = new AgentAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setName(req.getName().trim());
        agg.setSystemPrompt(req.getSystemPrompt());
        agg.setModelId(req.getModelId().trim());
        agg.setToolIds(req.getToolIds());
        agg.setMemoryPolicy(req.getMemoryPolicy() == null ? Map.of() : req.getMemoryPolicy());
        agg.setKnowledgeBasePolicy(req.getKnowledgeBasePolicy() == null ? Map.of() : req.getKnowledgeBasePolicy());
        agg.setCreatedAt(java.time.Instant.now());
        return agentRepository.save(agg);
    }

    public AgentDto getAgent(String id) {
        AgentAggregate agg = agentRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "agent not found", HttpStatus.NOT_FOUND));
        return toDto(agg);
    }

    public RunAgentResponse runAgent(String agentId, RunAgentRequest req) {
        AgentAggregate agentAgg = agentRepository.findById(agentId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "agent not found", HttpStatus.NOT_FOUND));

        String runtimeModelId = (req.getModelId() == null || req.getModelId().isBlank())
                ? agentAgg.getModelId()
                : req.getModelId().trim();
        if (runtimeModelId == null || runtimeModelId.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "modelId is required", HttpStatus.BAD_REQUEST);
        }
        ModelAggregate model = modelRepository.findById(runtimeModelId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "model not found", HttpStatus.NOT_FOUND));

        List<ToolAggregate> tools = toolRepository.findAll().stream()
                .filter(t -> agentAgg.getToolIds() != null && agentAgg.getToolIds().contains(t.getId()))
                .toList();

        Toolkit toolkit = toolExecutionService.buildToolkitForToolIds(tools);

        String sysPrompt = agentAgg.getSystemPrompt();
        String memoryContext = buildMemoryContext(agentAgg, req.getInput());
        if (!memoryContext.isBlank()) {
            sysPrompt = sysPrompt + "\n\n" + memoryContext;
        }
        Map<String, Object> mergedModelConfig = mergeConfig(model.getConfig(), req.getOptions());
        ModelDefinition modelDef = new ModelDefinition(
                model.getProvider(),
                model.getModelKey(),
                model.getApiKeyCipher(),
                model.getBaseUrl(),
                mergedModelConfig
        );

        io.agentscope.core.rag.Knowledge knowledge = buildKnowledge(agentAgg);
        AgentDefinition agentDef = knowledge != null
                ? new AgentDefinition(agentAgg.getName(), sysPrompt, modelDef, 10, knowledge,
                getKbTopK(agentAgg), getKbScoreThreshold(agentAgg))
                : new AgentDefinition(agentAgg.getName(), sysPrompt, modelDef, 10);

        Msg msg = agentRuntime.call(agentDef, req.getInput(), toolkit)
                .block(Duration.ofMinutes(2));

        String out = (msg == null || msg.getTextContent() == null) ? "" : msg.getTextContent();
        RunAgentResponse resp = new RunAgentResponse();
        resp.setOutput(out);
        return resp;
    }

    private String buildMemoryContext(AgentAggregate agentAgg, String input) {
        Map<String, Object> memoryPolicy = agentAgg.getMemoryPolicy() == null ? Map.of() : agentAgg.getMemoryPolicy();
        String ownerScope = JsonMaps.getString(memoryPolicy, POLICY_OWNER_SCOPE, "");
        if (ownerScope.isBlank()) {
            return "";
        }
        int topK = JsonMaps.getInt(memoryPolicy, POLICY_TOP_K, 3);
        MemoryQueryRequest mReq = new MemoryQueryRequest();
        mReq.setOwnerScope(ownerScope);
        mReq.setQueryText(input);
        mReq.setTopK(topK);
        var mResp = memoryApplicationService.query(mReq);
        if (mResp.getItems() == null || mResp.getItems().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[Memory]\n");
        for (var item : mResp.getItems()) {
            sb.append("- ").append(item.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建 AgentScope Knowledge，启用后由 ReActAgent 的 RAG 钩子注入检索结果。
     */
    private io.agentscope.core.rag.Knowledge buildKnowledge(AgentAggregate agentAgg) {
        Map<String, Object> kbPolicy = agentAgg.getKnowledgeBasePolicy() == null ? Map.of() : agentAgg.getKnowledgeBasePolicy();
        String kbKey = JsonMaps.getString(kbPolicy, POLICY_KB_KEY, "");
        if (kbKey.isBlank()) {
            return null;
        }
        int topK = getKbTopK(agentAgg);
        String embeddingModelId = JsonMaps.getString(kbPolicy, POLICY_EMBEDDING_MODEL_ID, "");
        return AgentLegoKnowledge.create(
                kbKey,
                embeddingModelId.isBlank() ? null : embeddingModelId,
                topK,
                getKbScoreThreshold(agentAgg),
                knowledgeBaseApplicationService
        );
    }

    private AgentDto toDto(AgentAggregate agg) {
        AgentDto dto = new AgentDto();
        dto.setId(agg.getId());
        dto.setName(agg.getName());
        dto.setSystemPrompt(agg.getSystemPrompt());
        dto.setModelId(agg.getModelId());
        dto.setToolIds(agg.getToolIds());
        dto.setMemoryPolicy(agg.getMemoryPolicy());
        dto.setKnowledgeBasePolicy(agg.getKnowledgeBasePolicy());
        dto.setCreatedAt(agg.getCreatedAt());
        if (agg.getModelId() != null && !agg.getModelId().isBlank()) {
            modelRepository.findById(agg.getModelId().trim()).ifPresent(m -> {
                dto.setModelDisplayName(m.getName());
                dto.setModelProvider(m.getProvider());
                dto.setModelModelKey(m.getModelKey());
                dto.setModelConfigSummary(ModelConfigSummaries.summarize(m.getConfig()));
            });
        }
        return dto;
    }

    private Map<String, Object> mergeConfig(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> safeBase = base == null ? Map.of() : base;
        Map<String, Object> safeOverride = override == null ? Map.of() : override;
        if (safeOverride.isEmpty()) {
            return safeBase;
        }
        java.util.HashMap<String, Object> merged = new java.util.HashMap<>(safeBase);
        merged.putAll(safeOverride);
        return merged;
    }
}

