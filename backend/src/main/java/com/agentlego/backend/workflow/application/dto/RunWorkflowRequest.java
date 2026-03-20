package com.agentlego.backend.workflow.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 运行工作流请求 DTO。
 */
@Data
public class RunWorkflowRequest {

    /**
     * 初始输入文本（作为工作流首个步骤的输入）。
     */
    @NotBlank
    private String input;
}

