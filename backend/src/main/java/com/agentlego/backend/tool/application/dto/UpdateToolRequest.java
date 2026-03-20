package com.agentlego.backend.tool.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 更新工具请求（PUT /tools/{id}）。
 * <p>
 * 与 {@link CreateToolRequest} 字段一致：整体覆盖 name / toolType / definition（id 与创建时间不可改）。
 */
@Data
public class UpdateToolRequest {

    @NotBlank
    private String toolType;

    @NotBlank
    private String name;

    /**
     * 工具定义；可为空对象。
     */
    private Map<String, Object> definition;
}
