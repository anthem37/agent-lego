package com.agentlego.backend.tool.application;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.tool.application.dto.CreateToolRequest;
import com.agentlego.backend.tool.application.dto.TestToolCallRequest;
import com.agentlego.backend.tool.application.dto.TestToolCallResponse;
import com.agentlego.backend.tool.application.dto.ToolDto;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
/**
 * 工具应用服务（Application Service）。
 *
 * 职责：
 * - 工具注册与查询（LOCAL/MCP）；
 * - 提供 test-call 能力（用于联调与健康检查）。
 *
 * 说明：
 * - 当前 test-call 仅支持 LOCAL 工具；
 * - MCP 工具执行将由 McpAdapter 接入后补齐。
 */
public class ToolApplicationService {
    private static final Duration TEST_CALL_TIMEOUT = Duration.ofSeconds(35);

    private final ToolRepository toolRepository;
    private final ToolExecutionService toolExecutionService;

    public ToolApplicationService(ToolRepository toolRepository, ToolExecutionService toolExecutionService) {
        this.toolRepository = toolRepository;
        this.toolExecutionService = toolExecutionService;
    }

    public String createTool(CreateToolRequest req) {
        ToolType toolType = parseToolType(req);
        String name = requireNonBlank(req.getName(), "name");

        ToolAggregate agg = new ToolAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setToolType(toolType);
        agg.setName(name.trim());
        agg.setDefinition(req.getDefinition() == null ? Map.of() : req.getDefinition());
        agg.setCreatedAt(Instant.now());

        return toolRepository.save(agg);
    }

    public List<ToolDto> listTools() {
        return toolRepository.findAll().stream().map(this::toDto).toList();
    }

    public ToolDto getTool(String id) {
        ToolAggregate agg = toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "tool not found", HttpStatus.NOT_FOUND));
        return toDto(agg);
    }

    public TestToolCallResponse testToolCall(String id, TestToolCallRequest req) {
        ToolAggregate agg = toolRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "tool not found", HttpStatus.NOT_FOUND));

        if (agg.getToolType() != ToolType.LOCAL) {
            throw new ApiException("UNSUPPORTED_TOOL_TYPE",
                    "MCP tool test-call is not implemented yet: " + agg.getName(),
                    HttpStatus.NOT_IMPLEMENTED);
        }

        Map<String, Object> input = (req == null || req.getInput() == null) ? Collections.emptyMap() : req.getInput();

        io.agentscope.core.message.ToolResultBlock result = toolExecutionService
                .executeLocalTool(agg.getName(), input)
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
    private ToolType parseToolType(CreateToolRequest req) {
        String toolTypeRaw = requireNonBlank(req.getToolType(), "toolType");
        try {
            return ToolType.valueOf(toolTypeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // 保持错误形态稳定，便于调用方处理。
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

