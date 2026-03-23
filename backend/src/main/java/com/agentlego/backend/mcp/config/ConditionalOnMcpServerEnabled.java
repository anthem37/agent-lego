package com.agentlego.backend.mcp.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.*;

/**
 * 本进程作为 MCP Server（SSE）启用时生效；与 {@code agentlego.mcp.server.*} 对齐，避免多处重复
 * {@code @ConditionalOnProperty}。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "agentlego.mcp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnMcpServerEnabled {
}
