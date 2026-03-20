package com.agentlego.backend.tool.domain;

public enum ToolType {
    /**
     * 进程内内置工具（如 echo / now）。
     */
    LOCAL,
    /**
     * MCP 外部工具（执行需 MCP 适配层接入）。
     */
    MCP,
    /**
     * 按 definition 发起 HTTP(S) 请求（url、method、headers 等）。
     */
    HTTP,
    /**
     * 调用平台工作流（注册可保存 workflowId；执行链路待接入）。
     */
    WORKFLOW
}

