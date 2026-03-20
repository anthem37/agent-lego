package com.agentlego.backend.kb.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateKbBaseRequest {
    /**
     * 绑定键（与智能体 knowledgeBasePolicy.kbKey 一致）。
     */
    @NotBlank
    private String kbKey;

    @NotBlank
    private String name;

    private String description;
}
