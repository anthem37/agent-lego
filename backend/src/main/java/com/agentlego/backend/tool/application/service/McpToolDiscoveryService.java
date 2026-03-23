package com.agentlego.backend.tool.application.service;

import com.agentlego.backend.api.ApiException;
import com.agentlego.backend.api.ApiRequires;
import com.agentlego.backend.mcp.client.McpClientRegistry;
import com.agentlego.backend.mcp.properties.McpClientProperties;
import com.agentlego.backend.tool.application.dto.BatchImportMcpToolsRequest;
import com.agentlego.backend.tool.application.dto.BatchImportMcpToolsResponse;
import com.agentlego.backend.tool.application.dto.CreateToolRequest;
import com.agentlego.backend.tool.application.dto.RemoteMcpToolMetaDto;
import com.agentlego.backend.tool.application.support.McpPlatformToolNaming;
import com.agentlego.backend.tool.domain.ToolRepository;
import com.agentlego.backend.tool.mcp.McpEndpointSecurity;
import com.agentlego.backend.tool.mcp.McpToolSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 外部 MCP 发现（tools/list）与批量导入为平台 MCP 工具；与核心 CRUD 分离，依赖 {@link ToolCreationService} 落库。
 */
@Service
public class McpToolDiscoveryService {

    private final McpClientRegistry mcpClientRegistry;
    private final McpClientProperties mcpClientProperties;
    private final ObjectMapper objectMapper;
    private final ToolRepository toolRepository;
    private final ToolCreationService toolCreationService;

    public McpToolDiscoveryService(
            McpClientRegistry mcpClientRegistry,
            McpClientProperties mcpClientProperties,
            ObjectMapper objectMapper,
            ToolRepository toolRepository,
            ToolCreationService toolCreationService
    ) {
        this.mcpClientRegistry = mcpClientRegistry;
        this.mcpClientProperties = mcpClientProperties;
        this.objectMapper = objectMapper;
        this.toolRepository = toolRepository;
        this.toolCreationService = toolCreationService;
    }

    public List<RemoteMcpToolMetaDto> listRemoteMcpTools(String endpoint, boolean refresh) {
        String ep = ApiRequires.nonBlank(endpoint, "endpoint");
        McpEndpointSecurity.validateEndpoint(ep, mcpClientProperties.isStrictSsrf());
        if (refresh) {
            mcpClientRegistry.invalidateRemoteToolsCache(ep);
        }
        List<McpSchema.Tool> tools = mcpClientRegistry.listRemoteTools(ep);
        return tools.stream().map(this::toRemoteMcpToolMeta).toList();
    }

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
            String platformName = McpPlatformToolNaming.resolvePlatformNameForRemote(req, prefix, remoteName);
            if (!McpPlatformToolNaming.isValidPlatformToolNameForImport(platformName)) {
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
            String platformLc = McpPlatformToolNaming.platformNameLower(platformName);
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
            String id = toolCreationService.createTool(create, true);
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
}
