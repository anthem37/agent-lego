package com.agentlego.backend.tool.application.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 工具语义分类元数据（供前端下拉与说明）。
 */
@Data
@Builder
public class ToolCategoryMetaDto {
    private String code;
    private String label;
    private String description;
}
