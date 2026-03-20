package com.agentlego.backend.mcp;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 SSE endpoint 复用 {@link McpClientWrapper}，供多个 MCP 类型平台工具共享同一外部 Server。
 */
@Component
public class McpClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpClientRegistry.class);
    private static final Duration INIT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration BUILD_TIMEOUT = Duration.ofSeconds(60);

    private final Map<String, McpClientWrapper> clients = new ConcurrentHashMap<>();
    private final Map<String, List<McpSchema.Tool>> toolsByEndpoint = new ConcurrentHashMap<>();

    /**
     * 同步获取已初始化客户端（首次连接会阻塞）。
     */
    public McpClientWrapper getOrCreate(String sseEndpoint) {
        if (sseEndpoint == null || sseEndpoint.isBlank()) {
            throw new IllegalArgumentException("MCP endpoint is blank");
        }
        String key = sseEndpoint.trim();
        return clients.computeIfAbsent(key, this::connect);
    }

    private McpClientWrapper connect(String url) {
        String name = "lego-mcp-" + Integer.toHexString(url.hashCode());
        log.info("Connecting MCP client '{}' → {}", name, url);
        McpClientWrapper w = McpClientBuilder.create(name)
                .sseTransport(url)
                .timeout(BUILD_TIMEOUT)
                .initializationTimeout(INIT_TIMEOUT)
                .buildSync();
        w.initialize().block(INIT_TIMEOUT);
        return w;
    }

    /**
     * 列出远端工具（按 endpoint 缓存；连接关闭时一并清空）。
     */
    public List<McpSchema.Tool> listRemoteTools(String sseEndpoint) {
        String key = sseEndpoint.trim();
        return toolsByEndpoint.computeIfAbsent(key, ep -> {
            McpClientWrapper c = getOrCreate(ep);
            List<McpSchema.Tool> list = c.listTools().block(INIT_TIMEOUT);
            return list == null ? List.of() : list;
        });
    }

    @PreDestroy
    public void shutdown() {
        toolsByEndpoint.clear();
        for (Map.Entry<String, McpClientWrapper> e : clients.entrySet()) {
            try {
                e.getValue().close();
            } catch (Exception ex) {
                log.warn("Error closing MCP client for {}: {}", e.getKey(), ex.toString());
            }
        }
        clients.clear();
    }
}
