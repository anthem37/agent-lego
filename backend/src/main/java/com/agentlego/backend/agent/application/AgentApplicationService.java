package com.agentlego.backend.agent.application;

import com.agentlego.backend.agent.application.assembler.AgentAssembler;
import com.agentlego.backend.agent.application.dto.AgentDto;
import com.agentlego.backend.agent.application.dto.CreateAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.application.KnowledgeBaseApplicationService;
import com.agentlego.backend.kb.rag.AgentLegoKnowledge;
import com.agentlego.backend.memory.application.MemoryApplicationService;
import com.agentlego.backend.memory.application.dto.MemoryQueryRequest;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.runtime.AgentRuntime;
import com.agentlego.backend.runtime.definition.AgentDefinition;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import com.agentlego.backend.tool.application.ToolExecutionService;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import io.agentscope.core.message.Msg;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.tool.Toolkit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AgentApplicationService {
    private static final String POLICY_OWNER_SCOPE = "ownerScope";
    private static final String POLICY_TOP_K = "topK";
    private static final String POLICY_KB_KEY = "kbKey";
    private static final String POLICY_EMBEDDING_MODEL_ID = "embeddingModelId";
    private static final String POLICY_SCORE_THRESHOLD = "scoreThreshold";

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
        return JsonMaps.getDouble(p, POLICY_SCORE_THRESHOLD, 0.3);
    }

    public String createAgent(CreateAgentRequest req) {
        if (req.getToolIds() == null) {
            req.setToolIds(Collections.emptyList());
        }
        modelRepository.findById(req.getModelId())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "模型未找到", HttpStatus.NOT_FOUND));
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
                .orElseThrow(() -> new ApiException("NOT_FOUND", "智能体未找到", HttpStatus.NOT_FOUND));
        Optional<ModelAggregate> modelOpt = (agg.getModelId() != null && !agg.getModelId().isBlank())
                ? modelRepository.findById(agg.getModelId().trim())
                : Optional.empty();
        return AgentAssembler.toDto(agg, modelOpt);
    }

    public RunAgentResponse runAgent(String agentId, RunAgentRequest req) {
        AgentAggregate agentAgg = agentRepository.findById(agentId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "智能体未找到", HttpStatus.NOT_FOUND));

        String runtimeModelId = (req.getModelId() == null || req.getModelId().isBlank())
                ? agentAgg.getModelId()
                : req.getModelId().trim();
        if (runtimeModelId != null) {
            runtimeModelId = runtimeModelId.trim();
        }
        ApiRequires.nonBlank(runtimeModelId, "modelId");
        ModelAggregate model = modelRepository.findById(runtimeModelId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "模型未找到", HttpStatus.NOT_FOUND));

        List<ToolAggregate> tools = toolRepository.findAll().stream()
                .filter(t -> agentAgg.getToolIds() != null && agentAgg.getToolIds().contains(t.getId()))
                .toList();

        Toolkit toolkit = toolExecutionService.buildToolkitForToolIds(tools);

        String sysPrompt = agentAgg.getSystemPrompt();
        String memoryContext = buildMemoryContext(agentAgg, req.getInput());
        if (!memoryContext.isBlank()) {
            sysPrompt = sysPrompt + "\n\n" + memoryContext;
        }
        Map<String, Object> mergedModelConfig = JsonMaps.shallowMerge(model.getConfig(), req.getOptions());
        ModelDefinition modelDef = new ModelDefinition(
                model.getProvider(),
                model.getModelKey(),
                model.getApiKeyCipher(),
                model.getBaseUrl(),
                mergedModelConfig
        );

        Knowledge knowledge = buildKnowledge(agentAgg);
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
        String body = mResp.getItems().stream()
                .map(item -> "- " + item.getContent())
                .collect(Collectors.joining("\n"));
        // 与历史 StringBuilder 行为一致：每条后均有换行（含最后一条）
        return "[Memory]\n" + body + "\n";
    }

    /**
     * 构建 AgentScope Knowledge，启用后由 ReActAgent 的 RAG 钩子注入检索结果。
     */
    private Knowledge buildKnowledge(AgentAggregate agentAgg) {
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

}


