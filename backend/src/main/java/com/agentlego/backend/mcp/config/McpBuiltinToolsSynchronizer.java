package com.agentlego.backend.mcp.config;

import com.agentlego.backend.mcp.adapter.McpAdapter;
import com.agentlego.backend.mcp.adapter.PlatformMcpServerBundle;
import com.agentlego.backend.tool.application.dto.LocalBuiltinToolMetaDto;
import com.agentlego.backend.tool.application.service.ToolExecutionService;

import java.util.List;

/**
 * 在运行时根据持久化的内置暴露策略，同步本进程 MCP Server 上的 tools/list。
 * <p>
 * 须由 {@link McpServerSpringConfiguration} 在 {@link PlatformMcpServerBundle} 注册为 Bean <strong>之后</strong>
 * 再声明为 Bean；勿使用 {@code @Component} + {@code @ConditionalOnBean}，否则组件扫描可能早于
 * {@code PlatformMcpServerBundle} 创建，条件不成立导致本类从未注册，「内置是否暴露到 MCP」变更无法同步 tools/list。
 */
public class McpBuiltinToolsSynchronizer {

    private final PlatformMcpServerBundle bundle;
    private final McpAdapter mcpAdapter;
    private final ToolExecutionService toolExecutionService;

    public McpBuiltinToolsSynchronizer(
            PlatformMcpServerBundle bundle,
            McpAdapter mcpAdapter,
            ToolExecutionService toolExecutionService
    ) {
        this.bundle = bundle;
        this.mcpAdapter = mcpAdapter;
        this.toolExecutionService = toolExecutionService;
    }

    public void syncTo(List<LocalBuiltinToolMetaDto> exposedForMcp) {
        mcpAdapter.syncLocalBuiltinTools(bundle.server(), toolExecutionService, exposedForMcp);
    }
}
