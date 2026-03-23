package com.agentlego.backend.tool.application.service;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.kb.domain.KbDocumentRepository;
import com.agentlego.backend.tool.application.dto.*;
import com.agentlego.backend.tool.application.mapper.ToolDtoMapper;
import com.agentlego.backend.tool.application.support.LocalToolResponseEnricher;
import com.agentlego.backend.tool.application.support.ToolWriteSupport;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 工具应用门面：分页/详情、更新、删除、引用查询、联调 test-call。
 * <p>
 * <strong>职责拆分</strong>：创建见 {@link ToolCreationService}；控制台元数据见 {@link ToolConsoleMetaService}；
 * MCP 远端发现与批量导入见 {@link McpToolDiscoveryService}；写入校验见 {@link ToolWriteSupport}。
 * <p>
 * 名称策略：工具 {@code name} 全平台唯一（大小写不敏感），与表 {@code lego_tools} 上索引 {@code ux_lego_tools_name_lower} 一致。
 */
@Service
public class ToolApplicationService {

    private static final Duration TEST_CALL_TIMEOUT = Duration.ofMinutes(4);
    private static final int TOOL_LIST_MAX_PAGE_SIZE = 200;

    private final ToolRepository toolRepository;
    private final ToolExecutionService toolExecutionService;
    private final AgentRepository agentRepository;
    private final LocalBuiltinToolCatalog localBuiltinToolCatalog;
    private final ToolDtoMapper toolDtoMapper;
    private final KbDocumentRepository kbDocumentRepository;
    private final ToolWriteSupport toolWriteSupport;
    private final ToolCreationService toolCreationService;
    private final ToolConsoleMetaService toolConsoleMetaService;
    private final McpToolDiscoveryService mcpToolDiscoveryService;
    private final LocalToolResponseEnricher localToolResponseEnricher;
    private final LocalBuiltinExposureApplicationService localBuiltinExposureApplicationService;

    public ToolApplicationService(
            ToolRepository toolRepository,
            ToolExecutionService toolExecutionService,
            AgentRepository agentRepository,
            LocalBuiltinToolCatalog localBuiltinToolCatalog,
            ToolDtoMapper toolDtoMapper,
            KbDocumentRepository kbDocumentRepository,
            ToolWriteSupport toolWriteSupport,
            ToolCreationService toolCreationService,
            ToolConsoleMetaService toolConsoleMetaService,
            McpToolDiscoveryService mcpToolDiscoveryService,
            LocalToolResponseEnricher localToolResponseEnricher,
            LocalBuiltinExposureApplicationService localBuiltinExposureApplicationService
    ) {
        this.toolRepository = toolRepository;
        this.toolExecutionService = toolExecutionService;
        this.agentRepository = agentRepository;
        this.localBuiltinToolCatalog = localBuiltinToolCatalog;
        this.toolDtoMapper = toolDtoMapper;
        this.kbDocumentRepository = kbDocumentRepository;
        this.toolWriteSupport = toolWriteSupport;
        this.toolCreationService = toolCreationService;
        this.toolConsoleMetaService = toolConsoleMetaService;
        this.mcpToolDiscoveryService = mcpToolDiscoveryService;
        this.localToolResponseEnricher = localToolResponseEnricher;
        this.localBuiltinExposureApplicationService = localBuiltinExposureApplicationService;
    }

    public String createTool(CreateToolRequest req) {
        return toolCreationService.createTool(req);
    }

    public void updateTool(String id, UpdateToolRequest req) {
        ToolAggregate existing = toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "工具未找到", HttpStatus.NOT_FOUND));

        ToolType toolType = toolWriteSupport.parseToolType(req.getToolType());
        String name = ApiRequires.nonBlank(req.getName(), "name");
        if (toolType == ToolType.LOCAL) {
            localBuiltinToolCatalog.requireSupportedLocalName(name);
        }
        Map<String, Object> definition = req.getDefinition() == null ? Map.of() : req.getDefinition();
        toolWriteSupport.validateDefinitionForType(toolType, definition);

        String trimmedName = name.trim();
        toolWriteSupport.requireGloballyUniqueToolName(trimmedName, id);

        existing.setToolType(toolType);
        existing.setToolCategory(toolWriteSupport.resolveToolCategory(req.getToolCategory()));
        existing.setName(name.trim());
        existing.setDisplayLabel(toolWriteSupport.normalizeOptionalLine(req.getDisplayLabel(), 256));
        existing.setDescription(toolWriteSupport.normalizeOptionalMultiline(req.getDescription(), 4000));
        existing.setDefinition(definition);
        toolRepository.update(existing);
    }

    public void deleteTool(String id) {
        toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "工具未找到", HttpStatus.NOT_FOUND));
        if (agentRepository.findToolReferencesByToolId(id).totalReferencingAgents() > 0) {
            throw new ApiException(
                    "CONFLICT",
                    "工具仍被智能体引用：请先从相关智能体的 toolIds 中移除此工具 ID 后再删除",
                    HttpStatus.CONFLICT
            );
        }
        if (kbDocumentRepository.countDocumentsReferencingToolId(id) > 0) {
            throw new ApiException(
                    "CONFLICT",
                    "工具仍被知识库文档引用：请先从相关文档的「绑定工具」中移除此工具后再删除",
                    HttpStatus.CONFLICT
            );
        }
        toolRepository.deleteById(id);
    }

    public List<LocalBuiltinToolMetaDto> listLocalBuiltins() {
        return toolConsoleMetaService.listLocalBuiltins();
    }

    public List<ToolCategoryMetaDto> listToolCategoryMeta() {
        return toolConsoleMetaService.listToolCategoryMeta();
    }

    public List<ToolTypeMetaDto> listToolTypeMeta() {
        return toolConsoleMetaService.listToolTypeMeta();
    }

    public List<RemoteMcpToolMetaDto> listRemoteMcpTools(String endpoint, boolean refresh) {
        return mcpToolDiscoveryService.listRemoteMcpTools(endpoint, refresh);
    }

    public BatchImportMcpToolsResponse batchImportMcpTools(BatchImportMcpToolsRequest req) {
        return mcpToolDiscoveryService.batchImportMcpTools(req);
    }

    /**
     * 管理端：已注册内置及 MCP/UI 暴露开关（与 {@code lego_local_builtin_tool_exposure} 对齐）。
     */
    public List<LocalBuiltinExposureRowDto> listLocalBuiltinExposureSettings() {
        return localBuiltinExposureApplicationService.listExposureSettings();
    }

    /**
     * 批量更新内置工具暴露策略；仅允许当前已注册内置中的工具名。
     */
    public void updateLocalBuiltinExposure(UpdateLocalBuiltinExposureRequest req) {
        localBuiltinExposureApplicationService.updateExposure(req);
    }

    public ToolPageDto listToolsPage(int page, int pageSize, String q, String toolType) {
        int p = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), TOOL_LIST_MAX_PAGE_SIZE);
        String qq = (q == null || q.isBlank()) ? null : q.trim();
        String tt = (toolType == null || toolType.isBlank()) ? null : toolType.trim();
        long total = toolRepository.countByQuery(qq, tt);
        long offset = (long) (p - 1) * size;
        var items = toolRepository.findPageByQuery(qq, tt, offset, size).stream()
                .map(toolDtoMapper::toDto)
                .map(localToolResponseEnricher::enrichIfLocal)
                .toList();
        return ToolPageDto.builder()
                .items(items)
                .total(total)
                .page(p)
                .pageSize(size)
                .build();
    }

    public ToolDto getTool(String id) {
        ToolAggregate agg = toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "工具未找到", HttpStatus.NOT_FOUND));
        return localToolResponseEnricher.enrichIfLocal(toolDtoMapper.toDto(agg));
    }

    public ToolReferencesDto getToolReferences(String id) {
        toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "工具未找到", HttpStatus.NOT_FOUND));
        var agentRefs = agentRepository.findToolReferencesByToolId(id);
        long kbDocs = kbDocumentRepository.countDocumentsReferencingToolId(id);
        return toolDtoMapper.toReferencesDto(
                agentRefs.totalReferencingAgents(),
                agentRefs.sampleAgentIds(),
                kbDocs
        );
    }

    public TestToolCallResponse testToolCall(String id, TestToolCallRequest req) {
        ToolAggregate agg = toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "工具未找到", HttpStatus.NOT_FOUND));

        Map<String, Object> input = (req == null || req.getInput() == null) ? Collections.emptyMap() : req.getInput();

        io.agentscope.core.message.ToolResultBlock result = toolExecutionService
                .executeTool(agg, input)
                .block(TEST_CALL_TIMEOUT);

        TestToolCallResponse resp = new TestToolCallResponse();
        resp.setResult(result);
        return resp;
    }
}
