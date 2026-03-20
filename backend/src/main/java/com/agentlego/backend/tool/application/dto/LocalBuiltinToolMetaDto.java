package com.agentlego.backend.tool.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 进程内 LOCAL 内置工具元数据（由后端扫描 {@code @Tool} 自动生成列表）。
 */
@Data
@Builder
public class LocalBuiltinToolMetaDto {
    /**
     * 工具名（与注册/调用一致，如 echo、now）。
     */
    private String name;
    /**
     * {@link io.agentscope.core.tool.Tool#description()}（多为英文技术说明）。
     */
    private String description;
    /**
     * 前端下拉展示用标签（可由 {@link com.agentlego.backend.tool.local.LocalBuiltinUiHint#label()} 提供）。
     */
    private String label;
    /**
     * 联调/使用提示（中文，可选）。
     */
    private String usageHint;
    /**
     * 工具方法入参（按声明顺序）。
     */
    private List<LocalBuiltinParamMetaDto> inputParameters;
    /**
     * 方法返回类型的简单类名，如 String。
     */
    private String outputJavaType;
    /**
     * {@link io.agentscope.core.tool.Tool#converter()} 的简单类名。
     */
    private String resultConverterClass;
    /**
     * 给前端展示的出参说明（一句话）。
     */
    private String outputDescription;
}
