package com.agentlego.backend.agent.web;

import com.agentlego.backend.agent.application.dto.AgentDto;
import com.agentlego.backend.agent.application.dto.CreateAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentRequest;
import com.agentlego.backend.agent.application.dto.RunAgentResponse;
import com.agentlego.backend.agent.application.service.AgentApplicationService;
import com.agentlego.backend.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP：智能体 CRUD 与同步运行。
 */
@RestController
@RequestMapping("/agents")
public class AgentController {

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
