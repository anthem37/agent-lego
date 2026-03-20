package com.agentlego.backend.tool.application.dto;

import lombok.Builder;
import lombok.Data;

/**
 * LOCAL 内置工具单个入参（来自方法参数上的 {@code @ToolParam} 与 Java 类型）。
 */
@Data
@Builder
public class LocalBuiltinParamMetaDto {
    private String name;
    private boolean required;
    private String description;
    /**
     * Java 参数类型简单名，如 String、int。
     */
    private String type;
}
