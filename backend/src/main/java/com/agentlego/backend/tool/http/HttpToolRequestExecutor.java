package com.agentlego.backend.tool.http;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 执行 {@link HttpToolSpec} 描述的 HTTP 调用（平台 HTTP 工具与推理运行时之间的桥接层）。
 * <p>
 * 默认实现为基于 <a href="https://square.github.io/okhttp/">OkHttp</a> 的 {@link OkHttpHttpToolRequestExecutor}。
 */
@FunctionalInterface
public interface HttpToolRequestExecutor {

    HttpToolExecutionResult execute(HttpToolSpec spec, Map<String, Object> input, ObjectMapper objectMapper);
}
