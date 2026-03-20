package com.agentlego.backend.tool;

import com.agentlego.backend.api.ApiResponse;
import com.agentlego.backend.tool.application.ToolApplicationService;
import com.agentlego.backend.tool.application.dto.CreateToolRequest;
import com.agentlego.backend.tool.application.dto.TestToolCallRequest;
import com.agentlego.backend.tool.application.dto.TestToolCallResponse;
import com.agentlego.backend.tool.application.dto.ToolDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具管理 API（Controller）。
 * <p>
 * 提供能力：
 * - 注册工具（LOCAL/MCP）
 * - 列表/详情查询
 * - 对工具发起一次 test-call（用于联调与健康检查）
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

    @GetMapping
    public ApiResponse<List<ToolDto>> list() {
        return ApiResponse.ok(toolService.listTools());
    }

    @GetMapping("/{id}")
    public ApiResponse<ToolDto> get(@PathVariable("id") String id) {
        return ApiResponse.ok(toolService.getTool(id));
    }

    @PostMapping("/{id}/test-call")
    public ApiResponse<TestToolCallResponse> testCall(
            @PathVariable("id") String id,
            @RequestBody(required = false) TestToolCallRequest req
    ) {
        return ApiResponse.ok(toolService.testToolCall(id, req));
    }
}

