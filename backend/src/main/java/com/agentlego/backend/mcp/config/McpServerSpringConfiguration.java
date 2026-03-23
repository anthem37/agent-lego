package com.agentlego.backend.mcp.config;

import com.agentlego.backend.mcp.adapter.McpAdapter;
import com.agentlego.backend.mcp.adapter.PlatformMcpServerBundle;
import com.agentlego.backend.mcp.properties.McpClientProperties;
import com.agentlego.backend.mcp.properties.McpServerProperties;
import com.agentlego.backend.tool.application.service.LocalBuiltinExposureApplicationService;
import com.agentlego.backend.tool.application.service.ToolExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;

/**
 * 装配本进程 MCP Server：{@link PlatformMcpServerBundle}（SSE + tools）、路由、进程退出时关闭 transport。
 * <p>开关见 {@link ConditionalOnMcpServerEnabled}（{@code agentlego.mcp.server.enabled}）。
 */
@Configuration
@EnableConfigurationProperties({McpServerProperties.class, McpClientProperties.class})
public class McpServerSpringConfiguration {

    @Bean
    @ConditionalOnMcpServerEnabled
    public PlatformMcpServerBundle platformMcpServerBundle(
            McpAdapter mcpAdapter,
            ObjectMapper objectMapper,
            ToolExecutionService toolExecutionService,
            LocalBuiltinExposureApplicationService localBuiltinExposureApplicationService,
            McpServerProperties props
    ) {
        return mcpAdapter.buildPlatformMcpServerBundle(
                objectMapper,
                props.getSsePath(),
                toolExecutionService,
                localBuiltinExposureApplicationService.listMetasForMcp()
        );
    }

    @Bean
    @ConditionalOnMcpServerEnabled
    public RouterFunction<ServerResponse> platformMcpRouterFunction(PlatformMcpServerBundle bundle) {
        return bundle.routerFunction();
    }

    @Bean
    @ConditionalOnMcpServerEnabled
    public McpServerLifecycle mcpServerLifecycle(PlatformMcpServerBundle bundle) {
        return new McpServerLifecycle(bundle);
    }

    public static final class McpServerLifecycle implements org.springframework.beans.factory.DisposableBean {

        private final PlatformMcpServerBundle bundle;

        McpServerLifecycle(PlatformMcpServerBundle bundle) {
            this.bundle = bundle;
        }

        @Override
        public void destroy() {
            bundle.transport().closeGracefully().block(Duration.ofSeconds(20));
        }
    }
}
