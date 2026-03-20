package com.agentlego.backend.tool.application;

import com.agentlego.backend.agent.domain.AgentRepository;
import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.tool.application.dto.*;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import com.agentlego.backend.tool.http.HttpToolSpec;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.agentlego.backend.tool.mcp.McpToolSpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具应用服务（Application Service）。
 * <p>
 * 职责：
 * - 工具注册与查询（LOCAL / MCP / HTTP / WORKFLOW 等）；
 * - 提供 test-call 能力（用于联调与健康检查）。
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

    private final ToolRepository toolRepository;
    private final ToolExecutionService toolExecutionService;
    private final AgentRepository agentRepository;
    private final LocalBuiltinToolCatalog localBuiltinToolCatalog;

    public ToolApplicationService(
            ToolRepository toolRepository,
            ToolExecutionService toolExecutionService,
            AgentRepository agentRepository,
            LocalBuiltinToolCatalog localBuiltinToolCatalog
    ) {
        this.toolRepository = toolRepository;
        this.toolExecutionService = toolExecutionService;
        this.agentRepository = agentRepository;
        this.localBuiltinToolCatalog = localBuiltinToolCatalog;
    }

    public String createTool(CreateToolRequest req) {
        ToolType toolType = parseToolType(req.getToolType());
        String name = requireNonBlank(req.getName(), "name");
        if (toolType == ToolType.LOCAL) {
            localBuiltinToolCatalog.requireSupportedLocalName(name);
        }

        Map<String, Object> definition = req.getDefinition() == null ? Map.of() : req.getDefinition();
        validateDefinitionForType(toolType, definition);

        if (toolRepository.existsByToolTypeAndName(toolType, name.trim())) {
            throw new ApiException(
                    "CONFLICT",
                    "同一类型下已存在同名工具: " + toolType + "/" + name.trim(),
                    HttpStatus.CONFLICT
            );
        }

        ToolAggregate agg = new ToolAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setToolType(toolType);
        agg.setName(name.trim());
        agg.setDefinition(definition);
        agg.setCreatedAt(Instant.now());

        return toolRepository.save(agg);
    }

    /**
     * 更新工具（类型、名称、定义均可变；id 不变）。
     */
    public void updateTool(String id, UpdateToolRequest req) {
        ToolAggregate existing = toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "tool not found", HttpStatus.NOT_FOUND));

        ToolType toolType = parseToolType(req.getToolType());
        String name = requireNonBlank(req.getName(), "name");
        if (toolType == ToolType.LOCAL) {
            localBuiltinToolCatalog.requireSupportedLocalName(name);
        }
        Map<String, Object> definition = req.getDefinition() == null ? Map.of() : req.getDefinition();
        validateDefinitionForType(toolType, definition);

        if (toolRepository.existsByToolTypeAndNameExcludingId(toolType, name.trim(), id)) {
            throw new ApiException(
                    "CONFLICT",
                    "同一类型下已存在同名工具: " + toolType + "/" + name.trim(),
                    HttpStatus.CONFLICT
            );
        }

        existing.setToolType(toolType);
        existing.setName(name.trim());
        existing.setDefinition(definition);
        toolRepository.update(existing);
    }

    /**
     * 删除工具；若仍被智能体 toolIds 引用则拒绝（409）。
     */
    public void deleteTool(String id) {
        toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "tool not found", HttpStatus.NOT_FOUND));
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
     * 工具类型元数据（前端表单、列表提示、能力开关）。
     */
    public List<ToolTypeMetaDto> listToolTypeMeta() {
        String localNames = localBuiltinToolCatalog.listMeta().stream()
                .map(LocalBuiltinToolMetaDto::getName)
                .collect(Collectors.joining("、"));
        String localDescription = localNames.isBlank()
                ? "由后端扫描 @Tool 自动生成；当前未发现内置实现。"
                : ("内置：" + localNames + "。名称须与内置名一致，零配置联调。");
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
                        .description("按 definition 调用外部 HTTP(S) API（含 SSRF 防护）。")
                        .supportsTestCall(true)
                        .build(),
                ToolTypeMetaDto.builder()
                        .code("WORKFLOW")
                        .label("工作流")
                        .description("绑定平台 workflowId，同步执行工作流。")
                        .supportsTestCall(true)
                        .build(),
                ToolTypeMetaDto.builder()
                        .code("MCP")
                        .label("MCP")
                        .description(
                                "登记外部 MCP Server（SSE URL 写入 definition.endpoint）；"
                                        + "可选 definition.mcpToolName 指定远端工具名（默认与平台工具 name 一致）。"
                                        + "本服务同时对外暴露 MCP（见配置 agentlego.mcp.server.sse-path，默认同源 /mcp）。"
                        )
                        .supportsTestCall(true)
                        .build()
        );
    }

    public List<ToolDto> listTools() {
        return toolRepository.findAll().stream().map(this::toDto).toList();
    }

    public ToolDto getTool(String id) {
        ToolAggregate agg = toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "tool not found", HttpStatus.NOT_FOUND));
        return toDto(agg);
    }

    /**
     * 查询工具被智能体引用情况（删除前提示）。
     */
    public ToolReferencesDto getToolReferences(String id) {
        toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "tool not found", HttpStatus.NOT_FOUND));
        ToolReferencesDto dto = new ToolReferencesDto();
        int n = agentRepository.countByToolId(id);
        dto.setReferencingAgentCount(n);
        dto.setReferencingAgentIds(agentRepository.listAgentIdsByToolId(id));
        return dto;
    }

    public TestToolCallResponse testToolCall(String id, TestToolCallRequest req) {
        ToolAggregate agg = toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "tool not found", HttpStatus.NOT_FOUND));

        Map<String, Object> input = (req == null || req.getInput() == null) ? Collections.emptyMap() : req.getInput();

        io.agentscope.core.message.ToolResultBlock result = toolExecutionService
                .executeTool(agg, input)
                .block(TEST_CALL_TIMEOUT);

        TestToolCallResponse resp = new TestToolCallResponse();
        resp.setResult(result);
        return resp;
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
                        "WORKFLOW tool requires definition.workflowId",
                        HttpStatus.BAD_REQUEST
                );
            }
        } else if (toolType == ToolType.MCP) {
            McpToolSpec.validateDefinition(definition);
        }
    }

    private ToolType parseToolType(String toolTypeRaw) {
        String t = requireNonBlank(toolTypeRaw, "toolType");
        try {
            return ToolType.valueOf(t.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", "invalid toolType: " + toolTypeRaw, HttpStatus.BAD_REQUEST);
        }
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException("VALIDATION_ERROR", fieldName + " is required", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private ToolDto toDto(ToolAggregate agg) {
        ToolDto dto = new ToolDto();
        dto.setId(agg.getId());
        dto.setToolType(agg.getToolType().name());
        dto.setName(agg.getName());
        dto.setDefinition(agg.getDefinition());
        dto.setCreatedAt(agg.getCreatedAt());
        return dto;
    }
}

