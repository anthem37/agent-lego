package com.agentlego.backend.tool.application.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 管理端：已注册内置工具 + 当前 MCP/UI 暴露开关。
 */
@Data
@Builder
public class LocalBuiltinExposureRowDto {

    private String name;
    private String label;
    private String description;
    /**
     * 是否在本机 MCP Server 的 tools/list 中对外暴露。
     */
    private boolean exposeMcp;
    /**
     * 是否在管理端「创建 LOCAL 工具」等下拉中展示。
     */
    private boolean showInUi;
}
