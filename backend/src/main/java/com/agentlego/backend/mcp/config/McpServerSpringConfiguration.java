package com.agentlego.backend.mcp.config;

import com.agentlego.backend.mcp.adapter.McpAdapter;
import com.agentlego.backend.mcp.adapter.PlatformMcpServerBundle;
import com.agentlego.backend.mcp.properties.McpClientProperties;
import com.agentlego.backend.mcp.properties.McpServerProperties;
import com.agentlego.backend.tool.application.service.LocalBuiltinExposureApplicationService;
import com.agentlego.backend.tool.application.service.ToolExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({McpServerProperties.class, McpClientProperties.class})
public class McpServerSpringConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "agentlego.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
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

    /**
     * 必须与 {@link #platformMcpServerBundle} 同配置类且声明在其后，保证 {@link PlatformMcpServerBundle} 已存在后再注册，
     * 否则 {@link LocalBuiltinExposureApplicationService} 更新暴露策略时无法同步 tools/list。
     */
    @Bean
    @ConditionalOnBean(PlatformMcpServerBundle.class)
    public McpBuiltinToolsSynchronizer mcpBuiltinToolsSynchronizer(
            PlatformMcpServerBundle bundle,
            McpAdapter mcpAdapter,
            ToolExecutionService toolExecutionService
    ) {
        return new McpBuiltinToolsSynchronizer(bundle, mcpAdapter, toolExecutionService);
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
