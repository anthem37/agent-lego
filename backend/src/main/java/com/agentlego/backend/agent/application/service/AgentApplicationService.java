package com.agentlego.backend.agent.application.service;

import com.agentlego.backend.agent.application.dto.*;
import com.agentlego.backend.agent.application.mapper.AgentDtoMapper;
import com.agentlego.backend.agent.domain.AgentAggregate;
import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.JsonMaps;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.kb.rag.KbRagKnowledgeFactory;
import com.agentlego.backend.kb.rag.KbRagSessionToolOutputs;
import com.agentlego.backend.kb.support.KbPolicies;
import com.agentlego.backend.memorypolicy.domain.MemoryItemRepository;
import com.agentlego.backend.memorypolicy.domain.MemoryPolicyRepository;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryItemDO;
import com.agentlego.backend.memorypolicy.infrastructure.persistence.MemoryPolicyDO;
import com.agentlego.backend.memorypolicy.runtime.LegoLongTermMemory;
import com.agentlego.backend.model.domain.ModelAggregate;
import com.agentlego.backend.model.domain.ModelProvider;
import com.agentlego.backend.model.domain.ModelRepository;
import com.agentlego.backend.runtime.AgentRuntime;
import com.agentlego.backend.runtime.definition.AgentDefinition;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import com.agentlego.backend.tool.application.service.ToolExecutionService;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
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

@Service
public class AgentApplicationService {

    public static final String RUNTIME_REACT = "REACT";
    public static final String RUNTIME_CHAT = "CHAT";

    static final String KB_RAG_PLACEHOLDER_HINT = """
            [知识库]
            后续消息中的「参考资料」可能含有：
            - {{tool_field:工具运行时名.出参路径}}：表示该处对应工具的 JSON 出参字段；若本轮已调用过该工具，片段中可能已替换为实际值；否则请先调用工具获取最新结果，勿编造。
            - 已展开为「…」工具的文案：表示知识建议调用的工具，仍请用标准工具调用执行。
            """.strip();

    private final AgentRepository agentRepository;
    private final ModelRepository modelRepository;
    private final ToolRepository toolRepository;
    private final ToolExecutionService toolExecutionService;
    private final AgentRuntime agentRuntime;
    private final KbRagKnowledgeFactory kbRagKnowledgeFactory;
    private final MemoryPolicyRepository memoryPolicyRepository;
    private final MemoryItemRepository memoryItemRepository;
    private final AgentDtoMapper agentDtoMapper;

    public AgentApplicationService(
            AgentRepository agentRepository,
            ModelRepository modelRepository,
            ToolRepository toolRepository,
            ToolExecutionService toolExecutionService,
            AgentRuntime agentRuntime,
            KbRagKnowledgeFactory kbRagKnowledgeFactory,
            MemoryPolicyRepository memoryPolicyRepository,
            MemoryItemRepository memoryItemRepository,
            AgentDtoMapper agentDtoMapper
    ) {
        this.agentRepository = agentRepository;
        this.modelRepository = modelRepository;
        this.toolRepository = toolRepository;
        this.toolExecutionService = toolExecutionService;
        this.agentRuntime = agentRuntime;
        this.kbRagKnowledgeFactory = kbRagKnowledgeFactory;
        this.memoryPolicyRepository = memoryPolicyRepository;
        this.memoryItemRepository = memoryItemRepository;
        this.agentDtoMapper = agentDtoMapper;
    }

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

    private static int resolveMaxReactIters(AgentAggregate agentAgg, boolean chatOnly) {
        if (chatOnly) {
            return 1;
        }
        Integer v = agentAgg.getMaxReactIters();
        int mi = (v == null || v < 1) ? 10 : v;
        return Math.min(mi, 64);
    }

    private static void normalizeToolIds(CreateAgentRequest req) {
        if (req.getToolIds() == null) {
            req.setToolIds(Collections.emptyList());
        }
    }

    private static String buildMemoryPreviewText(List<MemoryItemDO> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        final int maxLen = 2000;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            MemoryItemDO it = hits.get(i);
            String c = it.getContent() == null ? "" : it.getContent();
            if (i > 0) {
                sb.append("\n---\n");
            }
            sb.append("[").append(i + 1).append("] ").append(c);
            if (sb.length() >= maxLen) {
                return sb.substring(0, maxLen) + "…";
            }
        }
        return sb.toString();
    }

    private ModelAggregate requireChatModelByConfigId(String modelId) {
        ModelAggregate model = modelRepository.findById(modelId.trim())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "模型未找到", HttpStatus.NOT_FOUND));
        requireChatModelForAgent(model);
        return model;
    }

    /**
     * 将请求字段写入聚合（不处理 id / createdAt / 持久化）。
     */
    private void applyUpsertRequestToAggregate(AgentAggregate target, CreateAgentRequest req) {
        target.setName(req.getName().trim());
        target.setSystemPrompt(req.getSystemPrompt());
        target.setModelId(req.getModelId().trim());
        target.setToolIds(req.getToolIds());
        String mpId = req.getMemoryPolicyId() == null ? null : req.getMemoryPolicyId().trim();
        if (mpId != null && !mpId.isEmpty()) {
            memoryPolicyRepository.findById(mpId)
                    .orElseThrow(() -> new ApiException("NOT_FOUND", "记忆策略未找到", HttpStatus.NOT_FOUND));
            target.setMemoryPolicyId(mpId);
        } else {
            target.setMemoryPolicyId(null);
        }
        target.setKnowledgeBasePolicy(req.getKnowledgeBasePolicy() == null ? Map.of() : req.getKnowledgeBasePolicy());
        String rk = req.getRuntimeKind();
        target.setRuntimeKind(rk == null || rk.isBlank() ? RUNTIME_REACT : rk.trim().toUpperCase());
        Integer mri = req.getMaxReactIters();
        target.setMaxReactIters(mri == null || mri < 1 ? 10 : Math.min(mri, 64));
    }

    public String createAgent(CreateAgentRequest req) {
        normalizeToolIds(req);
        requireChatModelByConfigId(req.getModelId());
        AgentAggregate agg = new AgentAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        applyUpsertRequestToAggregate(agg, req);
        agg.setCreatedAt(java.time.Instant.now());
        return agentRepository.save(agg);
    }

    public void updateAgent(String id, CreateAgentRequest req) {
        normalizeToolIds(req);
        AgentAggregate existing = agentRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "智能体未找到", HttpStatus.NOT_FOUND));
        requireChatModelByConfigId(req.getModelId());
        applyUpsertRequestToAggregate(existing, req);
        agentRepository.update(existing);
    }

    public AgentDto getAgent(String id) {
        AgentAggregate agg = agentRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "智能体未找到", HttpStatus.NOT_FOUND));
        Optional<ModelAggregate> modelOpt = (agg.getModelId() != null && !agg.getModelId().isBlank())
                ? modelRepository.findById(agg.getModelId().trim())
                : Optional.empty();
        AgentDto dto = agentDtoMapper.toDto(agg, modelOpt);
        if (agg.getMemoryPolicyId() != null && !agg.getMemoryPolicyId().isBlank()) {
            memoryPolicyRepository.findById(agg.getMemoryPolicyId().trim()).ifPresent(p -> {
                dto.setMemoryPolicyName(p.getName());
                dto.setMemoryPolicyOwnerScope(p.getOwnerScope());
            });
        }
        return dto;
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

        String kind = agentAgg.getRuntimeKind() == null ? RUNTIME_REACT : agentAgg.getRuntimeKind().trim().toUpperCase();
        boolean chatOnly = RUNTIME_CHAT.equals(kind);
        List<ToolAggregate> tools = chatOnly
                ? List.of()
                : toolRepository.findAll().stream()
                .filter(t -> agentAgg.getToolIds() != null && agentAgg.getToolIds().contains(t.getId()))
                .toList();

        String sysPrompt = agentAgg.getSystemPrompt();

        boolean kbPolicyPresent = !KbPolicies.collectionIds(
                agentAgg.getKnowledgeBasePolicy() == null ? Map.of() : agentAgg.getKnowledgeBasePolicy()
        ).isEmpty();
        KbRagSessionToolOutputs kbSessionToolOutputs = kbPolicyPresent ? new KbRagSessionToolOutputs() : null;
        var kbBinding = kbRagKnowledgeFactory.resolve(agentAgg, kbSessionToolOutputs);
        Toolkit toolkit;
        if (kbBinding.isPresent()) {
            sysPrompt = sysPrompt + "\n\n" + KB_RAG_PLACEHOLDER_HINT;
            toolkit = toolExecutionService.buildToolkitForToolIds(tools, kbSessionToolOutputs);
        } else {
            toolkit = toolExecutionService.buildToolkitForToolIds(tools);
        }

        Map<String, Object> mergedModelConfig = JsonMaps.shallowMerge(model.getConfig(), req.getOptions());
        ModelDefinition modelDef = new ModelDefinition(
                model.getProvider(),
                model.getModelKey(),
                model.getApiKeyCipher(),
                model.getBaseUrl(),
                mergedModelConfig
        );

        int maxIters = resolveMaxReactIters(agentAgg, chatOnly);

        LongTermMemory longTermMemory = null;
        MemoryPolicyDO policyForMemory = null;
        int memTopK = 5;
        if (agentAgg.getMemoryPolicyId() != null && !agentAgg.getMemoryPolicyId().isBlank()) {
            policyForMemory = memoryPolicyRepository.findById(agentAgg.getMemoryPolicyId().trim())
                    .orElseThrow(() -> new ApiException("NOT_FOUND", "记忆策略未找到", HttpStatus.NOT_FOUND));
            memTopK = policyForMemory.getTopK() == null ? 5 : policyForMemory.getTopK();
            longTermMemory = new LegoLongTermMemory(
                    memoryItemRepository,
                    policyForMemory.getId(),
                    policyForMemory.getOwnerScope(),
                    policyForMemory.getStrategyKind(),
                    memTopK,
                    policyForMemory.getRetrievalMode(),
                    policyForMemory.getWriteMode(),
                    policyForMemory.getWriteBackOnDuplicate(),
                    agentAgg.getId()
            );
        }

        Knowledge knowledge = null;
        int kTop = 3;
        double kTh = 0.3;
        if (kbBinding.isPresent()) {
            var kb = kbBinding.get();
            knowledge = kb.knowledge();
            kTop = kb.topK();
            kTh = kb.scoreThreshold();
        }

        AgentDefinition agentDef = new AgentDefinition(
                agentAgg.getName(),
                sysPrompt,
                modelDef,
                maxIters,
                knowledge,
                kTop,
                kTh,
                longTermMemory,
                longTermMemory != null ? LongTermMemoryMode.STATIC_CONTROL : null
        );

        Msg msg = agentRuntime.call(agentDef, req.getInput(), toolkit)
                .block(Duration.ofMinutes(2));

        String out = (msg == null || msg.getTextContent() == null) ? "" : msg.getTextContent();
        RunAgentResponse resp = new RunAgentResponse();
        resp.setOutput(out);
        if (policyForMemory != null) {
            AgentRunMemoryDebug mem = new AgentRunMemoryDebug();
            mem.setMemoryPolicyId(policyForMemory.getId());
            mem.setMemoryPolicyName(policyForMemory.getName());
            mem.setOwnerScope(policyForMemory.getOwnerScope());
            mem.setRetrievalMode(policyForMemory.getRetrievalMode());
            mem.setWriteMode(policyForMemory.getWriteMode());
            String q = req.getInput() == null ? "" : req.getInput();
            List<MemoryItemDO> previewHits = memoryItemRepository.searchByKeyword(policyForMemory.getId(), q, memTopK);
            mem.setPreviewHitCount(previewHits.size());
            mem.setPreviewText(buildMemoryPreviewText(previewHits));
            resp.setMemory(mem);
        }
        return resp;
    }

}
