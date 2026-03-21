package com.agentlego.backend.tool.application.service;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.mcp.client.McpClientRegistry;
import com.agentlego.backend.mcp.properties.McpClientProperties;
import com.agentlego.backend.tool.application.dto.*;
import com.agentlego.backend.tool.application.mapper.ToolDtoMapper;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolCategory;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import com.agentlego.backend.tool.http.HttpToolSpec;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.agentlego.backend.tool.mcp.McpEndpointSecurity;
import com.agentlego.backend.tool.mcp.McpToolSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具应用服务（Application Service）。
 * <p>
 * 职责：
 * - 工具注册与查询（LOCAL / MCP / HTTP / WORKFLOW 等）；
 * - 提供 test-call 能力（用于联调与健康检查）。
 * <p>
 * 名称策略：工具 {@code name} 全平台唯一（大小写不敏感，任意 {@code toolType} 间也不可重名），与表
 * {@code lego_tools} 上索引 {@code ux_lego_tools_name_lower} 一致；不再按「类型 + 名称」单独判重。
 * <p>
 * 说明：
 * - test-call：LOCAL、HTTP、WORKFLOW、MCP 可执行（MCP 走外部 SSE endpoint）。
 */
@Service
public class ToolApplicationService {
    /**
     * 工作流工具可能串联多智能体，适当放宽上限。
     */
    private static final Duration TEST_CALL_TIMEOUT = Duration.ofMinutes(4);
    private static final int TOOL_LIST_MAX_PAGE_SIZE = 200;

    private final ToolRepository toolRepository;
    private final ToolExecutionService toolExecutionService;
    private final AgentRepository agentRepository;
    private final LocalBuiltinToolCatalog localBuiltinToolCatalog;
    private final McpClientRegistry mcpClientRegistry;
    private final ObjectMapper objectMapper;
    private final McpClientProperties mcpClientProperties;
    private final ToolDtoMapper toolDtoMapper;

    public ToolApplicationService(
            ToolRepository toolRepository,
            ToolExecutionService toolExecutionService,
            AgentRepository agentRepository,
            LocalBuiltinToolCatalog localBuiltinToolCatalog,
            McpClientRegistry mcpClientRegistry,
            ObjectMapper objectMapper,
            McpClientProperties mcpClientProperties,
            ToolDtoMapper toolDtoMapper
    ) {
        this.toolRepository = toolRepository;
        this.toolExecutionService = toolExecutionService;
        this.agentRepository = agentRepository;
        this.localBuiltinToolCatalog = localBuiltinToolCatalog;
        this.mcpClientRegistry = mcpClientRegistry;
        this.objectMapper = objectMapper;
        this.mcpClientProperties = mcpClientProperties;
        this.toolDtoMapper = toolDtoMapper;
    }

    /**
     * 平台工具 name 规则与前端一致：字母开头，仅 [A-Za-z0-9_-]。
     */
    static String sanitizePlatformToolName(String prefix, String remoteName) {
        String p = prefix == null ? "" : prefix.trim();
        String raw = p + Objects.requireNonNull(remoteName, "remoteName").trim();
        String s = raw.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (s.isEmpty()) {
            s = "mcp_tool";
        }
        char c0 = s.charAt(0);
        if (!Character.isLetter(c0)) {
            s = "mcp_" + s;
        }
        return s;
    }

    /**
     * 批量导入 MCP 时拟创建的平台工具名校验（与前端 NAME_ID_RULES 一致）。
     */
    private static boolean isValidPlatformToolNameForImport(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.matches("^[a-zA-Z][a-zA-Z0-9_-]*$");
    }

    private static String resolvePlatformNameForRemote(BatchImportMcpToolsRequest req, String prefix, String remoteName) {
        Map<String, String> overrides = req.getPlatformNamesByRemote();
        if (overrides != null) {
            String v = overrides.get(remoteName);
            if (v != null) {
                return v.trim();
            }
        }
        return sanitizePlatformToolName(prefix, remoteName);
    }

    private static String normalizeOptionalLine(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().replace('\n', ' ').replace('\r', ' ');
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= maxLen ? t : t.substring(0, maxLen);
    }

    private static String normalizeOptionalMultiline(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        String t = raw.strip();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= maxLen ? t : t.substring(0, maxLen);
    }

    public String createTool(CreateToolRequest req) {
        ToolType toolType = parseToolType(req.getToolType());
        String name = ApiRequires.nonBlank(req.getName(), "name");
        if (toolType == ToolType.LOCAL) {
            localBuiltinToolCatalog.requireSupportedLocalName(name);
        }

        Map<String, Object> definition = req.getDefinition() == null ? Map.of() : req.getDefinition();
        validateDefinitionForType(toolType, definition);

        String trimmedName = name.trim();
        requireGloballyUniqueToolName(trimmedName, null);

        ToolAggregate agg = new ToolAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setToolType(toolType);
        agg.setToolCategory(resolveToolCategory(req.getToolCategory()));
        agg.setName(name.trim());
        agg.setDisplayLabel(normalizeOptionalLine(req.getDisplayLabel(), 256));
        agg.setDescription(normalizeOptionalMultiline(req.getDescription(), 4000));
        agg.setDefinition(definition);
        agg.setCreatedAt(Instant.now());

        return toolRepository.save(agg);
    }

    /**
     * 更新工具（类型、名称、定义均可变；id 不变）。
     */
    public void updateTool(String id, UpdateToolRequest req) {
        ToolAggregate existing = toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "工具未找到", HttpStatus.NOT_FOUND));

        ToolType toolType = parseToolType(req.getToolType());
        String name = ApiRequires.nonBlank(req.getName(), "name");
        if (toolType == ToolType.LOCAL) {
            localBuiltinToolCatalog.requireSupportedLocalName(name);
        }
        Map<String, Object> definition = req.getDefinition() == null ? Map.of() : req.getDefinition();
        validateDefinitionForType(toolType, definition);

        String trimmedName = name.trim();
        requireGloballyUniqueToolName(trimmedName, id);

        existing.setToolType(toolType);
        existing.setToolCategory(resolveToolCategory(req.getToolCategory()));
        existing.setName(name.trim());
        existing.setDisplayLabel(normalizeOptionalLine(req.getDisplayLabel(), 256));
        existing.setDescription(normalizeOptionalMultiline(req.getDescription(), 4000));
        existing.setDefinition(definition);
        toolRepository.update(existing);
    }

    /**
     * 工具类型元数据（前端表单、列表提示、能力开关）。
     */

    /**
     * 删除工具；若仍被智能体 toolIds 引用则拒绝（409）。
     */
    public void deleteTool(String id) {
        toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "工具未找到", HttpStatus.NOT_FOUND));
        if (agentRepository.countByToolId(id) > 0) {
            throw new ApiException(
                    "CONFLICT",
                    "工具仍被智能体引用：请先从相关智能体的 toolIds 中移除此工具 ID 后再删除",
                    HttpStatus.CONFLICT
            );
        }
        toolRepository.deleteById(id);
    }

    /**
     * 进程内可执行的 LOCAL 内置工具清单（由后端扫描 {@code @Tool}）。
     */
    public List<LocalBuiltinToolMetaDto> listLocalBuiltins() {
        return localBuiltinToolCatalog.listMeta();
    }

    /**
     * 工具语义分类元数据（与 toolType 正交）。
     */
    public List<ToolCategoryMetaDto> listToolCategoryMeta() {
        return List.of(
                ToolCategoryMetaDto.builder()
                        .code("QUERY")
                        .label("查询工具")
                        .description("偏只读查询；知识库可将工具 JSON 出参字段映射到文档中的 {{占位符}}。")
                        .build(),
                ToolCategoryMetaDto.builder()
                        .code("ACTION")
                        .label("操作工具")
                        .description("可能产生副作用或通用工具（默认分类）。")
                        .build()
        );
    }

    public List<ToolTypeMetaDto> listToolTypeMeta() {
        String localNames = localBuiltinToolCatalog.listMeta().stream()
                .map(LocalBuiltinToolMetaDto::getName)
                .collect(Collectors.joining("、"));
        String asNameRule = "工具名将作为运行时注册名，须全平台唯一（大小写不敏感），并与模型侧工具调用约定一致。";
        String localDescription = localNames.isBlank()
                ? "由后端扫描 @Tool 自动生成；当前未发现内置实现。" + asNameRule
                : ("内置：" + localNames + "。名称须与内置名一致，零配置联调。" + asNameRule);
        return List.of(
                ToolTypeMetaDto.builder()
                        .code("LOCAL")
                        .label("本地内置")
                        .description(localDescription)
                        .supportsTestCall(true)
                        .build(),
                ToolTypeMetaDto.builder()
                        .code("HTTP")
                        .label("HTTP 请求")
                        .description("按 definition 调用外部 HTTP(S) API；出站请求使用 Square OkHttp（可配置超时，见 agentlego.tool.*），"
                                + "URL 经 SSRF 校验。"
                                + "definition.parameters / inputSchema 为 JSON Schema（常见工具协议子集），供模型理解与调用。"
                                + asNameRule)
                        .supportsTestCall(true)
                        .build(),
                ToolTypeMetaDto.builder()
                        .code("WORKFLOW")
                        .label("工作流")
                        .description("绑定平台 workflowId，同步执行工作流；运行时作为可调用工具返回结构化结果。" + asNameRule)
                        .supportsTestCall(true)
                        .build(),
                ToolTypeMetaDto.builder()
                        .code("MCP")
                        .label("MCP")
                        .description(
                                "登记外部 MCP Server（SSE URL 写入 definition.endpoint）；"
                                        + "可选 definition.mcpToolName 指定远端工具名（默认与平台工具 name 一致）。"
                                        + "可使用 GET /tools/meta/mcp/remote-tools 发现远端工具列表，"
                                        + "POST /tools/meta/mcp/batch-import 批量导入（默认同名记入 nameConflicts 可改名重试；skipExisting=true 则跳过）。"
                                        + "本服务同时对外暴露 MCP（见 agentlego.mcp.server.sse-path，默认同源 /mcp）。"
                                        + "SSRF：agentlego.mcp.client.strict-ssrf=true 时与 HTTP 工具一致禁止内网地址。"
                                        + "入参 Schema 建议与远端 MCP 工具定义一致。"
                                        + asNameRule
                        )
                        .supportsTestCall(true)
                        .build()
        );
    }

    /**
     * 连接外部 MCP 并返回 {@code tools/list}（可选刷新缓存）。
     */
    public List<RemoteMcpToolMetaDto> listRemoteMcpTools(String endpoint, boolean refresh) {
        String ep = ApiRequires.nonBlank(endpoint, "endpoint");
        McpEndpointSecurity.validateEndpoint(ep, mcpClientProperties.isStrictSsrf());
        if (refresh) {
            mcpClientRegistry.invalidateRemoteToolsCache(ep);
        }
        List<McpSchema.Tool> tools = mcpClientRegistry.listRemoteTools(ep);
        return tools.stream().map(this::toRemoteMcpToolMeta).toList();
    }

    /**
     * 按远端工具批量创建平台 MCP 工具（每条含 endpoint + mcpToolName，可选写入 description/inputSchema）。
     * <p>
     * 名称冲突：默认（{@code skipExisting} 为 false/省略）记入 {@code nameConflicts}，不中断整批；为 true 时记入 {@code skipped}。
     * 可通过 {@link BatchImportMcpToolsRequest#getPlatformNamesByRemote()} 为每条远端工具指定平台名以消除冲突。
     */
    public BatchImportMcpToolsResponse batchImportMcpTools(BatchImportMcpToolsRequest req) {
        if (req == null) {
            throw new ApiException("VALIDATION_ERROR", "请求体不能为空", HttpStatus.BAD_REQUEST);
        }
        String endpoint = ApiRequires.nonBlank(req.getEndpoint(), "endpoint");
        McpEndpointSecurity.validateEndpoint(endpoint, mcpClientProperties.isStrictSsrf());
        mcpClientRegistry.invalidateRemoteToolsCache(endpoint);
        List<McpSchema.Tool> remote = mcpClientRegistry.listRemoteTools(endpoint);
        Map<String, McpSchema.Tool> byRemoteName = remote.stream()
                .collect(Collectors.toMap(McpSchema.Tool::name, t -> t, (a, b) -> a));

        List<String> orderedRemoteNames;
        if (req.getRemoteToolNames() == null || req.getRemoteToolNames().isEmpty()) {
            orderedRemoteNames = remote.stream().map(McpSchema.Tool::name).toList();
        } else {
            orderedRemoteNames = req.getRemoteToolNames().stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
        }

        boolean skipExisting = Boolean.TRUE.equals(req.getSkipExisting());
        String prefix = req.getNamePrefix() == null ? "" : req.getNamePrefix().trim();

        List<BatchImportMcpToolsResponse.Created> created = new ArrayList<>();
        List<BatchImportMcpToolsResponse.Skipped> skipped = new ArrayList<>();
        List<BatchImportMcpToolsResponse.NameConflict> nameConflicts = new ArrayList<>();
        Set<String> batchReservedLower = new LinkedHashSet<>();

        for (String remoteName : orderedRemoteNames) {
            McpSchema.Tool rt = byRemoteName.get(remoteName);
            if (rt == null) {
                skipped.add(BatchImportMcpToolsResponse.Skipped.builder()
                        .name(remoteName)
                        .reason("远端 tools/list 中不存在该名称")
                        .build());
                continue;
            }
            String platformName = resolvePlatformNameForRemote(req, prefix, remoteName);
            if (!isValidPlatformToolNameForImport(platformName)) {
                String reason = "平台工具名无效：须字母开头，仅含字母、数字、下划线、短横线";
                if (skipExisting) {
                    skipped.add(BatchImportMcpToolsResponse.Skipped.builder()
                            .name(platformName.isEmpty() ? remoteName : platformName)
                            .reason(reason)
                            .build());
                } else {
                    nameConflicts.add(BatchImportMcpToolsResponse.NameConflict.builder()
                            .remoteToolName(remoteName)
                            .attemptedPlatformName(platformName)
                            .reason(reason)
                            .build());
                }
                continue;
            }
            String platformLc = platformName.toLowerCase(Locale.ROOT);
            boolean takenInDb = toolRepository.existsOtherWithNameIgnoreCase(platformName, null);
            boolean duplicateInBatch = batchReservedLower.contains(platformLc);
            if (takenInDb || duplicateInBatch) {
                String reason = duplicateInBatch
                        ? "本批导入中与其它行拟创建的平台名重复（全平台唯一）"
                        : "已存在全平台同名工具（任意类型）";
                if (skipExisting) {
                    skipped.add(BatchImportMcpToolsResponse.Skipped.builder()
                            .name(platformName)
                            .reason(reason)
                            .build());
                } else {
                    nameConflicts.add(BatchImportMcpToolsResponse.NameConflict.builder()
                            .remoteToolName(remoteName)
                            .attemptedPlatformName(platformName)
                            .reason(reason)
                            .build());
                }
                continue;
            }

            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put(McpToolSpec.KEY_ENDPOINT, endpoint.trim());
            definition.put(McpToolSpec.KEY_MCP_TOOL_NAME, remoteName);
            if (rt.description() != null && !rt.description().isBlank()) {
                definition.put("description", rt.description().trim());
            }
            if (rt.inputSchema() != null) {
                try {
                    Map<String, Object> schema = objectMapper.convertValue(
                            rt.inputSchema(),
                            new TypeReference<>() {
                            }
                    );
                    if (schema != null && !schema.isEmpty()) {
                        definition.put("inputSchema", schema);
                    }
                } catch (Exception ignored) {
                    // 省略无法序列化的 schema，运行时仍可从远端推断
                }
            }

            CreateToolRequest create = new CreateToolRequest();
            create.setToolType("MCP");
            create.setName(platformName);
            create.setDefinition(definition);
            String id = createTool(create);
            batchReservedLower.add(platformLc);
            created.add(BatchImportMcpToolsResponse.Created.builder()
                    .id(id)
                    .name(platformName)
                    .remoteToolName(remoteName)
                    .build());
        }

        return BatchImportMcpToolsResponse.builder()
                .created(created)
                .skipped(skipped)
                .nameConflicts(nameConflicts)
                .build();
    }

    private RemoteMcpToolMetaDto toRemoteMcpToolMeta(McpSchema.Tool t) {
        Map<String, Object> schema = null;
        if (t.inputSchema() != null) {
            try {
                schema = objectMapper.convertValue(t.inputSchema(), new TypeReference<>() {
                });
            } catch (Exception ignored) {
                schema = null;
            }
        }
        return RemoteMcpToolMetaDto.builder()
                .name(t.name())
                .description(t.description())
                .inputSchema(schema)
                .build();
    }

    /**
     * 分页列表；{@code q} 对 name / id / tool_type / definition 文本做 ilike 模糊匹配（可为 null 表示不过滤）；
     * {@code toolType} 精确匹配工具类型（大小写不敏感，可为 null 表示全部类型）。
     */
    public ToolPageDto listToolsPage(int page, int pageSize, String q, String toolType) {
        int p = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), TOOL_LIST_MAX_PAGE_SIZE);
        String qq = (q == null || q.isBlank()) ? null : q.trim();
        String tt = (toolType == null || toolType.isBlank()) ? null : toolType.trim();
        long total = toolRepository.countByQuery(qq, tt);
        long offset = (long) (p - 1) * size;
        var items = toolRepository.findPageByQuery(qq, tt, offset, size).stream().map(toolDtoMapper::toDto).toList();
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
        return toolDtoMapper.toDto(agg);
    }

    /**
     * 查询工具被智能体引用情况（删除前提示）。
     */
    public ToolReferencesDto getToolReferences(String id) {
        toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "工具未找到", HttpStatus.NOT_FOUND));
        int n = agentRepository.countByToolId(id);
        return toolDtoMapper.toReferencesDto(n, agentRepository.listAgentIdsByToolId(id));
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

    private ToolCategory resolveToolCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return ToolCategory.ACTION;
        }
        try {
            return ToolCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    "VALIDATION_ERROR",
                    "toolCategory 无效，允许：QUERY、ACTION",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * 解析工具类型。
     * <p>
     * 说明：对外接收字符串（DTO），对内转换为枚举（ToolType）。
     */
    private void validateDefinitionForType(ToolType toolType, Map<String, Object> definition) {
        if (toolType == ToolType.HTTP) {
            HttpToolSpec.validateDefinition(definition);
        } else if (toolType == ToolType.WORKFLOW) {
            Object wf = definition.get("workflowId");
            if (wf == null || String.valueOf(wf).isBlank()) {
                throw new ApiException(
                        "VALIDATION_ERROR",
                        "工作流工具需要 definition.workflowId",
                        HttpStatus.BAD_REQUEST
                );
            }
        } else if (toolType == ToolType.MCP) {
            McpToolSpec.validateDefinition(definition);
        }
    }

    private ToolType parseToolType(String toolTypeRaw) {
        String t = ApiRequires.nonBlank(toolTypeRaw, "toolType");
        try {
            return ToolType.valueOf(t.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", "无效的 toolType：" + toolTypeRaw, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 新建或更新时校验 {@code name} 不与任意类型的已有工具冲突（大小写不敏感）。
     *
     * @param excludeId 更新时排除自身工具 id；新建传 {@code null}
     */
    private void requireGloballyUniqueToolName(String trimmedName, String excludeId) {
        if (trimmedName == null || trimmedName.isEmpty()) {
            return;
        }
        if (toolRepository.existsOtherWithNameIgnoreCase(trimmedName, excludeId)) {
            boolean creating = excludeId == null || excludeId.isBlank();
            String msg = creating
                    ? "工具名称「" + trimmedName + "」已被占用。名称须全平台唯一（大小写不敏感），任意工具类型间也不可重名。"
                    : "工具名称「" + trimmedName + "」已被其它工具占用。名称须全平台唯一（大小写不敏感），任意工具类型间也不可重名。";
            throw new ApiException("CONFLICT", msg, HttpStatus.CONFLICT);
        }
    }

}

