package com.agentlego.backend.mcp;

import com.agentlego.backend.tool.application.ToolExecutionService;
import com.agentlego.backend.tool.local.LocalBuiltinToolCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;

/**
 * 注册本机 MCP Server 的 {@link RouterFunction} 与优雅关闭。
 */
@Configuration
@EnableConfigurationProperties({McpServerProperties.class, McpClientProperties.class})
public class McpServerSpringConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "agentlego.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PlatformMcpServerBundle platformMcpServerBundle(
            McpAdapter mcpAdapter,
            ObjectMapper objectMapper,
            ToolExecutionService toolExecutionService,
            LocalBuiltinToolCatalog localBuiltinToolCatalog,
            McpServerProperties props
    ) {
        return mcpAdapter.buildPlatformMcpServerBundle(
                objectMapper,
                props.getSsePath(),
                toolExecutionService,
                localBuiltinToolCatalog
        );
    }

    @Bean
    @ConditionalOnBean(PlatformMcpServerBundle.class)
    public RouterFunction<ServerResponse> platformMcpRouterFunction(PlatformMcpServerBundle bundle) {
        return bundle.routerFunction();
    }

    @Bean
    @ConditionalOnBean(PlatformMcpServerBundle.class)
    public McpServerLifecycle mcpServerLifecycle(PlatformMcpServerBundle bundle) {
        return new McpServerLifecycle(bundle);
    }

    /**
     * 关闭 SSE 传输（释放会话）。
     */
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
