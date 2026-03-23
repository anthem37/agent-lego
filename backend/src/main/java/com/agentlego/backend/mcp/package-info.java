/**
 * <h2>本模块：进程内 MCP Server（对外）+ MCP Client（连外部）</h2>
 * <p>
 * <b>角色划分</b>
 * <ul>
 *   <li><b>Server</b>：本服务通过 SSE 暴露 MCP，与智能体/IDE 的 MCP 协议对接；工具列表来自「平台 LOCAL 内置」且受
 *   {@link com.agentlego.backend.tool.application.service.LocalBuiltinExposureApplicationService} 持久化开关约束。</li>
 *   <li><b>Client</b>：作为 Agent 运行时的一部分，按工具 definition 连接<b>外部</b> MCP（见
 *   {@link com.agentlego.backend.mcp.client.McpClientRegistry}、{@link com.agentlego.backend.tool.mcp.McpProxyAgentTool}）。</li>
 * </ul>
 * <p>
 * <b>入口类</b>：{@link com.agentlego.backend.mcp.config.McpServerSpringConfiguration}（装配）、
 * {@link com.agentlego.backend.mcp.adapter.McpAdapter}（构建 transport + tools/list + 同步）、
 * {@link com.agentlego.backend.mcp.properties.McpServerProperties} / {@link com.agentlego.backend.mcp.properties.McpClientProperties}（配置）。
 * <p>
 * <b>配置键</b>：{@code agentlego.mcp.server.enabled}、{@code agentlego.mcp.server.sse-path}、{@code agentlego.mcp.client.strict-ssrf}（见 {@code application.yml}）。
 */
package com.agentlego.backend.mcp;
