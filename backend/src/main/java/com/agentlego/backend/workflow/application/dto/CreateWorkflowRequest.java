package com.agentlego.backend.workflow.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 创建工作流请求 DTO。
 */
@Data
public class CreateWorkflowRequest {

    /**
     * 工作流名称。
     */
    @NotBlank
    private String name;

    /**
     * 工作流定义（JSON object），例如 steps/mode 或 agentId/modelId。
     */
    private Map<String, Object> definition;
}

