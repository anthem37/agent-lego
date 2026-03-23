package com.agentlego.backend.tool.web;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.tool.application.dto.*;
import com.agentlego.backend.tool.application.service.ToolApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP：工具 CRUD、分页、引用查询、联调 test-call。元数据与 MCP 发现见 {@link ToolMetaController}。
 */
@RestController
@RequestMapping("/tools")
public class ToolController {

    private final ToolApplicationService toolService;

    public ToolController(ToolApplicationService toolService) {
        this.toolService = toolService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> create(@Valid @RequestBody CreateToolRequest req) {
        return ApiResponse.created(toolService.createTool(req));
    }

    @GetMapping
    public ApiResponse<ToolPageDto> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String toolType
    ) {
        return ApiResponse.ok(toolService.listToolsPage(page, pageSize, q, toolType));
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
