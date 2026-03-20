package com.agentlego.backend.tool.http;

/**
 * HTTP 工具单次调用的执行结果（由 {@link HttpToolRequestExecutor} 产生，供 {@link HttpProxyAgentTool} 转为
 * {@link io.agentscope.core.message.ToolResultBlock}）。
 */
public sealed interface HttpToolExecutionResult {

    /**
     * 已收到 HTTP 响应（含非 2xx 状态码；由调用方决定是否视为错误）。
     */
    record Success(
            int statusCode,
            String body,
            String contentType,
            String resolvedUrl,
            String method
    ) implements HttpToolExecutionResult {
    }

    /**
     * 校验失败、序列化失败或网络层错误（未形成有效业务响应时）。
     */
    record Failure(String message) implements HttpToolExecutionResult {
    }
}
