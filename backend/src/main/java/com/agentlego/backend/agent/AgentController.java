package com.agentlego.backend.agent;

import com.agentlego.backend.agent.application.AgentApplicationService;
import com.agentlego.backend.agent.application.dto.AgentDto;
import com.agentlego.backend.agent.application.dto.CreateAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 智能体管理 API（Controller）。
 * <p>
 * 提供能力：
 * - 创建智能体（systemPrompt + toolIds + memory/KB policy）
 * - 查询智能体
 * - 运行智能体（同步返回最终 output）
 */
@RestController
@RequestMapping("/agents")
public class AgentController {

    /**
     * 智能体应用服务（Application Service）。
     */
    private final AgentApplicationService service;

    public AgentController(AgentApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<String> create(@Valid @RequestBody CreateAgentRequest req) {
        return ApiResponse.created(service.createAgent(req));
    }

    @GetMapping("/{id}")
    public ApiResponse<AgentDto> get(@PathVariable("id") String id) {
        return ApiResponse.ok(service.getAgent(id));
    }

    @PostMapping("/{id}/run")
    public ApiResponse<RunAgentResponse> run(
            @PathVariable("id") String id,
            @Valid @RequestBody RunAgentRequest req
    ) {
        return ApiResponse.ok(service.runAgent(id, req));
    }
}

