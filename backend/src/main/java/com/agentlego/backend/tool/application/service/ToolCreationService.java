package com.agentlego.backend.tool.application.service;

import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.common.SnowflakeIdGenerator;
import com.agentlego.backend.tool.application.dto.CreateToolRequest;
import com.agentlego.backend.tool.application.support.ToolWriteSupport;
import com.agentlego.backend.tool.domain.ToolAggregate;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.domain.ToolType;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * 工具创建（落库）；与更新、删除、元数据、MCP 发现解耦，便于批量导入等场景复用。
 */
@Service
public class ToolCreationService {

    private final ToolRepository toolRepository;
    private final LocalBuiltinToolCatalog localBuiltinToolCatalog;
    private final ToolWriteSupport toolWriteSupport;

    public ToolCreationService(
            ToolRepository toolRepository,
            LocalBuiltinToolCatalog localBuiltinToolCatalog,
            ToolWriteSupport toolWriteSupport
    ) {
        this.toolRepository = toolRepository;
        this.localBuiltinToolCatalog = localBuiltinToolCatalog;
        this.toolWriteSupport = toolWriteSupport;
    }

    public String createTool(CreateToolRequest req) {
        return createTool(req, false);
    }

    /**
     * @param skipGlobalNameUniquenessCheck 为 true 时跳过全平台重名校验（调用方已保证，如 MCP 批量导入预检）。
     */
    public String createTool(CreateToolRequest req, boolean skipGlobalNameUniquenessCheck) {
        ToolType toolType = toolWriteSupport.parseToolType(req.getToolType());
        String name = ApiRequires.nonBlank(req.getName(), "name");
        if (toolType == ToolType.LOCAL) {
            localBuiltinToolCatalog.requireSupportedLocalName(name);
        }

        Map<String, Object> definition = req.getDefinition() == null ? Map.of() : req.getDefinition();
        toolWriteSupport.validateDefinitionForType(toolType, definition);

        String trimmedName = name.trim();
        if (!skipGlobalNameUniquenessCheck) {
            toolWriteSupport.requireGloballyUniqueToolName(trimmedName, null);
        }

        ToolAggregate agg = new ToolAggregate();
        agg.setId(SnowflakeIdGenerator.nextId());
        agg.setToolType(toolType);
        agg.setToolCategory(toolWriteSupport.resolveToolCategory(req.getToolCategory()));
        agg.setName(name.trim());
        agg.setDisplayLabel(toolWriteSupport.normalizeOptionalLine(req.getDisplayLabel(), 256));
        agg.setDescription(toolWriteSupport.normalizeOptionalMultiline(req.getDescription(), 4000));
        agg.setDefinition(definition);
        agg.setCreatedAt(Instant.now());

        return toolRepository.save(agg);
    }
}
