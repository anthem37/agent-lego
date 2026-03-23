package com.agentlego.backend.tool.application.support;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.tool.domain.ToolCategory;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import com.agentlego.backend.tool.http.HttpToolSpec;
import com.agentlego.backend.tool.mcp.McpToolSpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * 工具写入路径共享逻辑：类型解析、definition 校验、全平台重名检测、可选字段规范化。
 * <p>
 * 供 {@link com.agentlego.backend.tool.application.service.ToolCreationService} 与
 * {@link com.agentlego.backend.tool.application.service.ToolApplicationService}（更新）复用，避免校验规则分叉。
 */
@Component
public class ToolWriteSupport {

    private final ToolRepository toolRepository;

    public ToolWriteSupport(ToolRepository toolRepository) {
        this.toolRepository = toolRepository;
    }

    public String normalizeOptionalLine(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().replace('\n', ' ').replace('\r', ' ');
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= maxLen ? t : t.substring(0, maxLen);
    }

    public String normalizeOptionalMultiline(String raw, int maxLen) {
        if (raw == null) {
            return null;
        }
        String t = raw.strip();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= maxLen ? t : t.substring(0, maxLen);
    }

    public ToolCategory resolveToolCategory(String raw) {
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

    public ToolType parseToolType(String toolTypeRaw) {
        String t = ApiRequires.nonBlank(toolTypeRaw, "toolType");
        try {
            return ToolType.valueOf(t.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException("VALIDATION_ERROR", "无效的 toolType：" + toolTypeRaw, HttpStatus.BAD_REQUEST);
        }
    }

    public void validateDefinitionForType(ToolType toolType, Map<String, Object> definition) {
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

    /**
     * @param excludeId 更新时排除自身工具 id；新建传 {@code null}
     */
    public void requireGloballyUniqueToolName(String trimmedName, String excludeId) {
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
