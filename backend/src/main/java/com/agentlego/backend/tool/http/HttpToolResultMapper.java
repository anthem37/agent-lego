package com.agentlego.backend.tool.http;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolCallParam;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将 {@link HttpToolExecutionResult} 转为 {@link ToolResultBlock}（纯映射，无 I/O）。
 */
public final class HttpToolResultMapper {

    private HttpToolResultMapper() {
    }

    /**
     * 把 HTTP 执行结果映射为模型/运行时可见的 {@link ToolResultBlock}。
     */
    public static ToolResultBlock toToolResultBlock(ToolCallParam param, HttpToolExecutionResult result) {
        Objects.requireNonNull(param, "param");
        Objects.requireNonNull(result, "result");
        if (result instanceof HttpToolExecutionResult.Failure failure) {
            return ToolResultBlock.error(failure.message());
        }
        if (result instanceof HttpToolExecutionResult.Success success) {
            return toSuccessBlock(param, success);
        }
        throw new IllegalStateException("Unexpected HttpToolExecutionResult: " + result.getClass());
    }

    private static ToolResultBlock toSuccessBlock(ToolCallParam param, HttpToolExecutionResult.Success success) {
        Map<String, Object> meta = new HashMap<>(4);
        meta.put("statusCode", success.statusCode());
        meta.put("url", success.resolvedUrl());
        meta.put("method", success.method());
        if (success.contentType() != null && !success.contentType().isBlank()) {
            meta.put("contentType", success.contentType());
        }
        return ToolResultBlock.of(
                param.getToolUseBlock().getId(),
                param.getToolUseBlock().getName(),
                TextBlock.builder().text(success.body()).build(),
                meta
        );
    }
}
