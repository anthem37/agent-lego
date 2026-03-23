package com.agentlego.backend.tool.web;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.tool.application.dto.*;
import com.agentlego.backend.tool.application.service.ToolApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具管理「元数据」与 MCP 发现类接口，与 {@link ToolController}（CRUD / 联调）拆分，便于路由与文档分组。
 */
@RestController
@RequestMapping("/tools/meta")
public class ToolMetaController {

    private final ToolApplicationService toolService;

    public ToolMetaController(ToolApplicationService toolService) {
        this.toolService = toolService;
    }

    @GetMapping("/tool-types")
    public ApiResponse<List<ToolTypeMetaDto>> toolTypeMeta() {
        return ApiResponse.ok(toolService.listToolTypeMeta());
    }

    @GetMapping("/tool-categories")
    public ApiResponse<List<ToolCategoryMetaDto>> toolCategoryMeta() {
        return ApiResponse.ok(toolService.listToolCategoryMeta());
    }

    @GetMapping("/local-builtins")
    public ApiResponse<List<LocalBuiltinToolMetaDto>> localBuiltinsMeta() {
        return ApiResponse.ok(toolService.listLocalBuiltins());
    }

    /**
     * 已注册内置工具及 MCP/UI 暴露开关（供管理端配置）。
     */
    @GetMapping("/local-builtins/exposure")
    public ApiResponse<List<LocalBuiltinExposureRowDto>> localBuiltinExposureSettings() {
        return ApiResponse.ok(toolService.listLocalBuiltinExposureSettings());
    }

    @PutMapping("/local-builtins/exposure")
    public ApiResponse<Void> updateLocalBuiltinExposure(@Valid @RequestBody UpdateLocalBuiltinExposureRequest body) {
        toolService.updateLocalBuiltinExposure(body);
        return ApiResponse.ok(null);
    }

    @GetMapping("/mcp/remote-tools")
    public ApiResponse<List<RemoteMcpToolMetaDto>> remoteMcpTools(
            @RequestParam("endpoint") String endpoint,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh
    ) {
        return ApiResponse.ok(toolService.listRemoteMcpTools(endpoint, refresh));
    }

    @PostMapping("/mcp/batch-import")
    public ApiResponse<BatchImportMcpToolsResponse> batchImportMcpTools(
            @Valid @RequestBody BatchImportMcpToolsRequest body
    ) {
        return ApiResponse.ok(toolService.batchImportMcpTools(body));
    }
}
