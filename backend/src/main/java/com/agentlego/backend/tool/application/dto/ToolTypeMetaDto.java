package com.agentlego.backend.tool.application.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 工具类型元数据（供前端动态渲染表单与提示）。
 */
@Data
@Builder
public class ToolTypeMetaDto {
    private String code;
    private String label;
    private String description;
    /**
     * 详情页 / test-call 是否预期可用。
     */
    private boolean supportsTestCall;
}
