package com.agentlego.backend.tool.http;

/**
 * HTTP 工具在发起请求前对目标 URL 做安全校验（默认实现为 {@link SsrHttpToolUrlValidator}，委托 {@link SsrUrlGuard}）。
 */
@FunctionalInterface
public interface HttpToolUrlValidator {

    void validate(String url);
}
