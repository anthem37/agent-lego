package com.agentlego.backend.tool.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 进程内 LOCAL 内置工具元数据（由后端聚合 {@code @Tool} 注册信息生成列表）。
 */
@Data
@Builder
public class LocalBuiltinToolMetaDto {
    /**
     * 工具名（与注册/调用一致）。
     */
    private String name;
    /**
     * 内置工具说明（多为英文技术说明）。
     */
    private String description;
    /**
     * 前端下拉展示用标签；与内置 {@code @Tool#name()} 一致（可另用平台侧 {@code displayLabel}）。
     */
    private String label;
    /**
     * 预留：联调提示；当前为空，可由平台工具说明或 {@code @Tool} description 覆盖。
     */
    private String usageHint;
    /**
     * 工具方法入参（按声明顺序）。
     */
    private List<LocalBuiltinParamMetaDto> inputParameters;
    /**
     * 与 HTTP 工具 {@code parameters} 对齐的 JSON Schema（object），由后端从入参列表生成。
     */
    private Map<String, Object> inputSchema;
    /**
     * 出参 JSON Schema：纯文本类内置多为根级 {@code type:string}；其它转换器可能为带 {@code properties} 的 object，便于控制台展示。
     */
    private Map<String, Object> outputSchema;
    /**
     * 方法返回类型的简单类名，如 String。
     */
    private String outputJavaType;
    /**
     * 结果转换器类的简单类名。
     */
    private String resultConverterClass;
    /**
     * 给前端展示的出参说明（一句话）。
     */
    private String outputDescription;
}
