package com.agentlego.backend.tool.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    /**
     * 可选；语义分类：QUERY | ACTION（默认 ACTION）。
     */
    private String toolCategory;

    /**
     * 工具名称（name）：全平台唯一（大小写不敏感），不可与其它工具记录冲突（更新时排除自身 id）。
     */
    @NotBlank
    private String name;

    @Size(max = 256)
    private String displayLabel;

    @Size(max = 4000)
    private String description;

    /**
     * 工具定义；可为空对象。
     */
    private Map<String, Object> definition;
}
