package com.agentlego.backend.eval.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 创建评测请求 DTO。
 */
@Data
public class CreateEvaluationRequest {

    /**
     * 被评测的智能体 ID。
     */
    @NotBlank
    private String agentId;

    /**
     * 评测使用的模型 ID。
     */
    @NotBlank
    private String modelId;

    /**
     * 评测名称。
     */
    @NotBlank
    private String name;

    /**
     * 评测用例列表（cases）。
     */
    @NotEmpty
    private List<EvalCaseDto> cases;
}

