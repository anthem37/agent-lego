package com.agentlego.backend.agent.application.service;

import com.agentlego.backend.agent.application.dto.AgentDto;
import com.agentlego.backend.agent.application.dto.CreateAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.application.mapper.AgentDtoMapper;
import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.application.KbRagKnowledgeFactory;
import com.agentlego.backend.memory.application.dto.MemoryQueryRequest;
import com.agentlego.backend.memory.application.service.MemoryApplicationService;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelProvider;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.runtime.AgentRuntime;
import com.agentlego.backend.runtime.definition.AgentDefinition;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import com.agentlego.backend.tool.application.service.ToolExecutionService;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AgentApplicationService {
    private static final String POLICY_OWNER_SCOPE = "ownerScope";
    private static final String POLICY_TOP_K = "topK";

    private final AgentRepository agentRepository;
    private final ModelRepository modelRepository;
    private final ToolRepository toolRepository;
    private final ToolExecutionService toolExecutionService;
    private final AgentRuntime agentRuntime;
    private final MemoryApplicationService memoryApplicationService;
    private final KbRagKnowledgeFactory kbRagKnowledgeFactory;
    private final AgentDtoMapper agentDtoMapper;

    public AgentApplicationService(
            AgentRepository agentRepository,
            ModelRepository modelRepository,
            ToolRepository toolRepository,
            ToolExecutionService toolExecutionService,
            AgentRuntime agentRuntime,
            MemoryApplicationService memoryApplicationService,
            KbRagKnowledgeFactory kbRagKnowledgeFactory,
            AgentDtoMapper agentDtoMapper
    ) {
        this.agentRepository = agentRepository;
        this.modelRepository = modelRepository;
        this.toolRepository = toolRepository;
        this.toolExecutionService = toolExecutionService;
        this.agentRuntime = agentRuntime;
        this.memoryApplicationService = memoryApplicationService;
        this.kbRagKnowledgeFactory = kbRagKnowledgeFactory;
        this.agentDtoMapper = agentDtoMapper;
    }

    /**
     * 智能体推理仅允许「聊天类」模型配置；文本嵌入类配置仅用于向量化等场景。
     */
    private static void requireChatModelForAgent(ModelAggregate model) {
        if (model.getProvider() == null || model.getProvider().isBlank()) {
            throw new ApiException(
                    "INVALID_AGENT_MODEL",
                    "模型缺少 provider，无法绑定到智能体",
                    HttpStatus.BAD_REQUEST
            );
        }
        final ModelProvider p;
        try {
            p = ModelProvider.from(model.getProvider());
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    "INVALID_AGENT_MODEL",
                    "不支持的模型提供方：" + model.getProvider(),
                    HttpStatus.BAD_REQUEST
            );
        }
        if (!p.isChatProvider()) {
            throw new ApiException(
                    "INVALID_AGENT_MODEL",
                    "智能体必须绑定聊天类模型配置，不能绑定文本嵌入（*_TEXT_EMBEDDING）类配置",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    public String createAgent(CreateAgentRequest req) {
        if (req.getToolIds() == null) {
            req.setToolIds(Collections.emptyList());
        }
        ModelAggregate boundModel = modelRepository.findById(req.getModelId())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "模型未找到", HttpStatus.NOT_FOUND));
        requireChatModelForAgent(boundModel);
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
        return agentDtoMapper.toDto(agg, modelOpt);
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
        requireChatModelForAgent(model);

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

        AgentDefinition agentDef;
        var kbBinding = kbRagKnowledgeFactory.resolve(agentAgg);
        if (kbBinding.isPresent()) {
            var kb = kbBinding.get();
            agentDef = new AgentDefinition(
                    agentAgg.getName(),
                    sysPrompt,
                    modelDef,
                    10,
                    kb.knowledge(),
                    kb.topK(),
                    kb.scoreThreshold()
            );
        } else {
            agentDef = new AgentDefinition(agentAgg.getName(), sysPrompt, modelDef, 10);
        }

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
        return "[Memory]\n" + body + "\n";
    }

}
