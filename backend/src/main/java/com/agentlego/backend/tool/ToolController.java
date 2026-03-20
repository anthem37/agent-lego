package com.agentlego.backend.tool;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.tool.application.ToolApplicationService;
import com.agentlego.backend.tool.application.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具管理 API（Controller）。
 * <p>
 * 提供能力：
 * - 注册工具（LOCAL / MCP / HTTP / WORKFLOW 等）
 * - 列表/详情/更新/删除
 * - 工具类型元数据（前端动态表单）
 * - 工具被智能体引用查询（删除前校验）
 * - test-call（联调）
 */
@RestController
@RequestMapping("/tools")
public class ToolController {

    /**
     * 工具应用服务（Application Service）。
     */
    private final ToolApplicationService toolService;

    public ToolController(ToolApplicationService toolService) {
        this.toolService = toolService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> create(@Valid @RequestBody CreateToolRequest req) {
        String id = toolService.createTool(req);
        return ApiResponse.created(id);
    }

    /**
     * 分页列表；{@code q} 可选，模糊匹配名称、ID、类型、definition 文本；
     * {@code toolType} 可选，按工具类型精确筛选（与枚举值一致，如 LOCAL / MCP）。
     */
    @GetMapping
    public ApiResponse<ToolPageDto> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String toolType
    ) {
        return ApiResponse.ok(toolService.listToolsPage(page, pageSize, q, toolType));
    }

    /**
     * 必须在 {@code GET /{id}} 之前声明，避免路径被当作 id。
     */
    @GetMapping("/meta/tool-types")
    public ApiResponse<List<ToolTypeMetaDto>> toolTypeMeta() {
        return ApiResponse.ok(toolService.listToolTypeMeta());
    }

    /**
     * 必须在 {@code GET /{id}} 之前声明，避免路径被当作 id。
     */
    @GetMapping("/meta/local-builtins")
    public ApiResponse<List<LocalBuiltinToolMetaDto>> localBuiltinsMeta() {
        return ApiResponse.ok(toolService.listLocalBuiltins());
    }

    /**
     * 连接外部 MCP（SSE URL）并返回远端 {@code tools/list}，供前端勾选批量导入。
     *
     * @param endpoint SSE 根地址（URL 编码）
     * @param refresh  为 true 时忽略缓存重新拉取
     */
    @GetMapping("/meta/mcp/remote-tools")
    public ApiResponse<List<RemoteMcpToolMetaDto>> remoteMcpTools(
            @RequestParam("endpoint") String endpoint,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh
    ) {
        return ApiResponse.ok(toolService.listRemoteMcpTools(endpoint, refresh));
    }

    /**
     * 将远端 MCP 工具批量登记为平台 MCP 工具（每条 definition 含 endpoint、mcpToolName，可选 description/inputSchema）。
     */
    @PostMapping("/meta/mcp/batch-import")
    public ApiResponse<BatchImportMcpToolsResponse> batchImportMcpTools(
            @Valid @RequestBody BatchImportMcpToolsRequest body
    ) {
        return ApiResponse.ok(toolService.batchImportMcpTools(body));
    }

    @GetMapping("/{id}")
    public ApiResponse<ToolDto> get(@PathVariable("id") String id) {
        return ApiResponse.ok(toolService.getTool(id));
    }

    @GetMapping("/{id}/references")
    public ApiResponse<ToolReferencesDto> references(@PathVariable("id") String id) {
        return ApiResponse.ok(toolService.getToolReferences(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable("id") String id, @Valid @RequestBody UpdateToolRequest req) {
        toolService.updateTool(id, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id) {
        toolService.deleteTool(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/test-call")
    public ApiResponse<TestToolCallResponse> testCall(
            @PathVariable("id") String id,
            @RequestBody(required = false) TestToolCallRequest req
    ) {
        return ApiResponse.ok(toolService.testToolCall(id, req));
    }
}

