package com.agentlego.backend.mcp.client;

import cn.hutool.core.util.StrUtil;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外连 MCP：按 endpoint 复用 {@link McpClientWrapper}，并缓存 {@code tools/list} 供管理端发现；
 * {@link #invalidateRemoteToolsCache} 在导入前强制重拉。
 */
@Slf4j
@Component
public class McpClientRegistry {
    private static final Duration INIT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration BUILD_TIMEOUT = Duration.ofSeconds(60);

    private final Map<String, McpClientWrapper> clients = new ConcurrentHashMap<>();
    private final Map<String, List<McpSchema.Tool>> toolsByEndpoint = new ConcurrentHashMap<>();

    public McpClientWrapper getOrCreate(String sseEndpoint) {
        if (StrUtil.isBlank(sseEndpoint)) {
            throw new IllegalArgumentException("MCP endpoint is blank");
        }
        String key = StrUtil.trim(sseEndpoint);
        return clients.computeIfAbsent(key, this::connect);
    }

    private McpClientWrapper connect(String url) {
        String name = "lego-mcp-" + Integer.toHexString(url.hashCode());
        log.info("Connecting MCP client '{}' -> {}", name, url);
        McpClientWrapper w = McpClientBuilder.create(name)
                .sseTransport(url)
                .timeout(BUILD_TIMEOUT)
                .initializationTimeout(INIT_TIMEOUT)
                .buildSync();
        w.initialize().block(INIT_TIMEOUT);
        return w;
    }

    public List<McpSchema.Tool> listRemoteTools(String sseEndpoint) {
        String key = StrUtil.trim(sseEndpoint);
        return toolsByEndpoint.computeIfAbsent(key, ep -> {
            McpClientWrapper c = getOrCreate(ep);
            List<McpSchema.Tool> list = c.listTools().block(INIT_TIMEOUT);
            return list == null ? List.of() : list;
        });
    }

    public void invalidateRemoteToolsCache(String sseEndpoint) {
        if (StrUtil.isBlank(sseEndpoint)) {
            return;
        }
        toolsByEndpoint.remove(StrUtil.trim(sseEndpoint));
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
