package com.agentlego.backend.tool.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 按远端工具名批量登记平台 MCP 工具。
 */
@Data
public class BatchImportMcpToolsRequest {

    /**
     * 外部 MCP SSE 根 URL。
     */
    @NotBlank(message = "endpoint 为必填")
    private String endpoint;

    /**
     * 要导入的远端工具名；{@code null} 或空列表表示导入远端全部工具。
     */
    private List<String> remoteToolNames;

    /**
     * 平台工具名前缀（可选），实际名称为 {@code prefix + 远端工具名}（经 {@link #sanitizePlatformToolName} 处理）。
     */
    private String namePrefix;

    /**
     * 平台已存在同名（同类型 MCP）时是否跳过；默认 true。
     */
    private Boolean skipExisting;
}
